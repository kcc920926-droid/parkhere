package com.drivewatch.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.drivewatch.core.Frame
import com.drivewatch.core.FrameResult
import com.drivewatch.core.HistoryFrame
import com.drivewatch.core.Signal
import com.drivewatch.core.collect.CollectorConfig
import com.drivewatch.core.collect.DriveMonitor
import com.drivewatch.core.collect.LiveWindow
import com.drivewatch.core.collect.MonitorUpdate
import com.drivewatch.core.collect.SignalEvent
import com.drivewatch.core.collect.SignalSource
import com.drivewatch.core.collect.TripReport
import kotlin.math.roundToInt

/**
 * 화면 뒤에서 차량 신호를 받아 구간마다 판정한다.
 * 주행 중에는 내비가 화면 앞에 있고, 화면에 없는 앱은 시스템이 멈춰 세운다. 그래서 포그라운드 서비스로 돈다.
 * 주차 후 앱을 열면 [state]의 마지막 주행 결과로 카드를 그린다.
 */
class DriveCollectorService : Service() {
    /** 서비스가 무엇으로 판정할지. 시작하기 전에 [configure]로 정한다. */
    data class Setup(
        val history: List<HistoryFrame>,
        val config: CollectorConfig = CollectorConfig(),
        val context: String = "city",
        val margins: Map<Signal, Double> = emptyMap(),
    )

    /** 화면이 그릴 현재 상태. */
    data class State(
        val sourceLabel: String,
        val live: LiveWindow,
        val tripActive: Boolean,
        /** 진행 중이거나 마지막으로 끝난 주행의 구간들. */
        val windows: List<Pair<Frame, FrameResult>>,
        /** 마지막으로 끝난 주행의 결과. 아직 없으면 null. */
        val lastReport: TripReport?,
        val issue: String?,
    )

    inner class Link : Binder() {
        val service: DriveCollectorService get() = this@DriveCollectorService
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var setup: Setup
    private lateinit var monitor: DriveMonitor
    private var source: SignalSource? = null
    private var sourceLabel = ""
    private val windows = mutableListOf<Pair<Frame, FrameResult>>()
    private var lastReport: TripReport? = null
    private var issue: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            monitor.tick(SystemClock.elapsedRealtime()).forEach(::record)
            main.postDelayed(this, TICK_MS)
        }
    }

    val state: State
        get() = State(sourceLabel, monitor.live, monitor.live.tripActive, windows.toList(), lastReport, issue)

    override fun onCreate() {
        super.onCreate()
        setup = checkNotNull(pendingSetup) { "DriveCollectorService.configure()를 먼저 부르세요" }
        monitor = DriveMonitor(setup.history, setup.config, setup.context, setup.margins)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("DriveWatch")
            .setContentText("주행 신호를 기록하고 있어요")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        useVehicle()
        main.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = Link()

    /** 차량 신호를 받는다. */
    fun useVehicle() = switchTo("차량 신호", VehicleSignalSource(this) { text ->
        Log.w(TAG, "issue $text")
        issue = text
    })

    /** 녹화한 신호를 흘린다. 화면에는 시연용으로 표기해야 한다. */
    fun useReplay(events: List<SignalEvent>) = switchTo("녹화 신호 (시연용)", ReplaySignalSource(events))

    private fun switchTo(label: String, next: SignalSource) {
        source?.stop()
        monitor = DriveMonitor(setup.history, setup.config, setup.context, setup.margins)
        windows.clear()
        lastReport = null
        issue = null
        sourceLabel = label
        source = next
        Log.i(TAG, "source $label")
        next.start { event -> monitor.accept(event).forEach(::record) }
    }

    private fun record(update: MonitorUpdate) {
        when (update) {
            is MonitorUpdate.TripStarted -> {
                windows.clear()
                Log.i(TAG, "trip started")
            }
            is MonitorUpdate.WindowJudged -> {
                windows += update.frame to update.result
                val values = update.frame.values
                Log.i(
                    TAG,
                    "window ${windows.size} hardBrakes=${values[Signal.HARD_BRAKES]?.toInt()} " +
                        "steering=${values[Signal.STEERING_CORRECTIONS]?.toInt()} " +
                        "speedKph=${update.frame.speedKph?.roundToInt()} observation=${update.result.observation.key}",
                )
            }
            is MonitorUpdate.Arrived -> {
                lastReport = update.report
                val found = update.report.detection?.let { d ->
                    "${d.evidence.joinToString { it.signal.key }}@${(d.minute * 60).roundToInt()}s"
                } ?: "none"
                Log.i(TAG, "arrived windows=${update.report.frames.size} detection=$found")
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        source?.stop()
        super.onDestroy()
    }

    companion object {
        const val TAG = "DWCOLLECT"
        private const val CHANNEL = "drivewatch.collect"
        private const val NOTIFICATION_ID = 41
        private const val TICK_MS = 250L
        private var pendingSetup: Setup? = null

        /** 서비스를 시작하기 전에 판정 기준을 정한다. */
        fun configure(setup: Setup) {
            pendingSetup = setup
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DriveCollectorService::class.java))
        }
    }
}
