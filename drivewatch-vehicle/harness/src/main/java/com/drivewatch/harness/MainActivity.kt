package com.drivewatch.harness

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.drivewatch.core.Observation
import com.drivewatch.core.Signal
import com.drivewatch.core.collect.CollectorConfig
import com.drivewatch.core.collect.TripReport
import com.drivewatch.core.demo.DemoDrive
import com.drivewatch.core.demo.DemoReplays
import com.drivewatch.vehicle.DriveCollectorService
import com.drivewatch.vehicle.VehicleSignalSource
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 수집 서비스가 차량 신호를 받아 구간마다 판정하는지 확인하는 화면이다. 제품 화면이 아니다.
 * 시연 모드로 돈다: 구간은 8초이고, 평소 기록은 합성 기록이다.
 * 수집은 [DriveCollectorService]가 하고, 이 화면은 그 상태를 그리기만 한다.
 */
class MainActivity : Activity() {
    private val config = CollectorConfig.demo()
    private val main = Handler(Looper.getMainLooper())
    private var service: DriveCollectorService? = null

    private lateinit var sourceLabel: TextView
    private lateinit var live: TextView
    private lateinit var card: TextView
    private lateinit var windows: TextView
    private lateinit var issues: TextView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as DriveCollectorService.Link).service
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val painter = object : Runnable {
        override fun run() {
            service?.state?.let(::render)
            main.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun text(size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            textSize = size
            setTextColor(color)
            setPadding(0, 12, 0, 12)
        }
        sourceLabel = text(20f, Color.rgb(160, 174, 192))
        live = text(26f)
        card = text(34f).apply { setPadding(32, 28, 32, 28); setBackgroundColor(Color.rgb(27, 37, 48)) }
        windows = text(22f, Color.rgb(203, 213, 225))
        issues = text(20f, Color.rgb(251, 146, 60))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply { text = "차량 신호"; textSize = 22f; setOnClickListener { service?.useVehicle() } })
            addView(Button(context).apply {
                text = "녹화 신호 (시연용)"; textSize = 22f
                setOnClickListener { service?.useReplay(DemoDrive.events(config.windowMs)) }
            })
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            listOf(sourceLabel, buttons, live, card, windows, issues).forEach(::addView)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 23, 32)); addView(column) })

        val wanted = listOf(VehicleSignalSource.SPEED_PERMISSION, "android.permission.POST_NOTIFICATIONS")
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) startCollector() else requestPermissions(wanted.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startCollector()
    }

    private fun startCollector() {
        DriveCollectorService.configure(DriveCollectorService.Setup(DemoReplays.history, config, context = "city"))
        DriveCollectorService.start(this)
        bindService(Intent(this, DriveCollectorService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        main.post(painter)
    }

    override fun onStop() {
        main.removeCallbacks(painter)
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        super.onDestroy()
    }

    private fun render(state: DriveCollectorService.State) {
        sourceLabel.text = "${state.sourceLabel} · 시연 모드(구간 ${config.windowMs / 1000}초, 합성 평소 기록)"
        val now = state.live
        val speed = now.speedKph?.let { "${it.roundToInt()}km/h" } ?: "차속 없음"
        val brake = when (now.brakePressed) { true -> "브레이크 눌림"; false -> "브레이크 해제"; null -> "브레이크 신호 없음" }
        val decel = now.deceleration?.let { String.format(Locale.US, "감속 %.1fm/s²", it) } ?: "감속 —"
        live.text = "${now.gear ?: "기어 없음"}단 · $speed · $brake · $decel\n" +
            if (state.tripActive) "${now.windowIndex + 1}번째 구간 · 강한 제동 ${now.hardBrakes}회 · 조향 보정 ${now.steeringCorrections}회" else "주행 아님"
        card.text = when {
            state.tripActive -> "주행 중이에요"
            state.lastReport != null -> arrivalCard(state.lastReport!!)
            else -> "주행을 기다리는 중이에요"
        }
        windows.text = state.windows.mapIndexed { index, (frame, result) ->
            val mark = when (result.observation) {
                Observation.CHANGE -> "  ← 두 구간 연속으로 평소를 넘었어요"
                Observation.WATCHING -> "  ← 평소를 넘었어요"
                else -> ""
            }
            "${span(index + 1)} · 강한 제동 ${count(frame.values[Signal.HARD_BRAKES])} · 조향 보정 ${count(frame.values[Signal.STEERING_CORRECTIONS])}$mark"
        }.joinToString("\n")
        issues.text = state.issue ?: ""
    }

    private fun count(value: Double?) = value?.let { "${it.toInt()}회" } ?: "값 없음"

    private fun span(index: Int): String {
        val seconds = config.windowMs / 1000
        return "출발 후 ${(index - 1) * seconds}–${index * seconds}초"
    }

    /** 주차 후 카드: 짧은 제목, 근거 한 줄, 어느 구간이었는지. */
    private fun arrivalCard(report: TripReport): String {
        val detection = report.detection
        if (detection == null) {
            val compared = report.results.any { result -> result.readings.any { it.value != null && it.baseline != null } }
            return if (compared) "평소와 비슷했어요" else "평소 주행을 알아가는 중이에요"
        }
        val signal = detection.evidence.first().signal
        val upper = report.baselines[signal]?.upper ?: return "평소와 달라요"
        val above = report.frames.mapIndexedNotNull { index, frame ->
            frame.values[signal]?.takeIf { it > upper }?.let { "${span(index + 1)} ${it.toInt()}${signal.unit}" }
        }
        val worst = report.frames.mapNotNull { it.values[signal] }.max()
        return String.format(
            Locale.KOREA, "평소와 달라요\n%s · %d초 구간당 평소 %d%s 이하, 이번 최고 %d%s\n%s",
            signal.label, config.windowMs / 1000, upper.toInt(), signal.unit, worst.toInt(), signal.unit, above.joinToString(", "),
        )
    }
}
