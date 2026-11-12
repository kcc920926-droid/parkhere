package com.parkhere.drivewatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * One drive. Lives in a foreground service because in D the navigation app takes the screen and
 * background activities are frozen. Gear D starts the drive and P ends it when the real vehicle is
 * there; otherwise (phone emulator) the scripted drive runs on its own. Navigation events are 시연용.
 */
class DriveService : Service(), VehicleLink.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var link: VehicleLink? = null
    private var tts: Speaker? = null
    private var profileId = "" // the driver this drive belongs to (stable across list changes)
    private var vehicle = false
    private var started = false
    private var startedAt = 0L
    private var finished = false
    private var spoken = 0
    private val signalSpans = mutableListOf<LongArray>() // [on, off]; off = Long.MAX_VALUE while on
    private var braking = false
    private var countedThisPress = false
    private val speeds = ArrayDeque<Pair<Long, Double>>()
    private var replay: ReplayLink? = null
    private var replaying = false
    /** Where the signals in the log come from. */
    private val src get() = if (replaying) "시연 재생" else "차량 신호"

    /** Real signals. While a 시연 재생 runs they are ignored, except that real movement stops the replay. */
    private val real = object : VehicleLink.Listener {
        override fun onGear(drive: Boolean, park: Boolean) { if (!replaying) this@DriveService.onGear(drive, park) }
        override fun onTurnSignal(on: Boolean) { if (!replaying) this@DriveService.onTurnSignal(on) }
        override fun onBrake(pressed: Boolean) { if (!replaying) this@DriveService.onBrake(pressed) }
        override fun onSpeed(mps: Double) { if (!replaying) this@DriveService.onSpeed(mps) else if (mps > 1.0) abortReplay() }
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun edit(f: (Drive) -> Drive) { AppState.drive?.let { AppState.drive = f(it) } }
    private fun log(line: String) = edit { it.copy(log = it.log + line) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (intent?.action == ACTION_REPLAY) { startReplay(); return START_NOT_STICKY }
        if (link != null) return START_NOT_STICKY
        profileId = AppState.profile.id
        tts = Speaker(this)
        link = VehicleLink(this, real).also { it.start() }
        AppState.vehicleNote = "차량 신호 확인 중"
        // No Pleos vehicle service (a phone emulator, a laptop demo): play the drive instead of waiting for a gear.
        main.postDelayed({ if (!vehicle && !replaying) startReplay() }, 3_000)
        return START_NOT_STICKY
    }

    // ---- 시연 재생 ----

    /** Plays this destination's scenario through the same handlers real signals use. */
    private fun startReplay() {
        val d = AppState.drive ?: return
        if (replaying || started || finished) return
        replaying = true
        AppState.vehicleNote = "시연 재생 준비 중"
        Scenarios.drive(this, d.place, d.comfortable) { timeline, from ->
            if (!replaying) return@drive
            if (timeline == null) { replaying = false; AppState.vehicleNote = "시연 재생 없음 · 안내 스크립트"; vehicle = false; begin(); return@drive }
            vehicle = true
            AppState.vehicleNote = "시연 재생 · $from"
            edit { it.copy(vehicle = true, replay = "${timeline.title} · $from") }
            log("시연 재생 · ${timeline.title} ($from)")
            replay = ReplayLink(timeline, this) { step(it) }.also { it.start() }
        }
    }

    private fun abortReplay() {
        if (!replaying) return
        replay?.stop(); replay = null
        replaying = false
        main.removeCallbacksAndMessages(null)
        started = false; spoken = 0
        log("차가 실제로 움직여서 시연 재생을 멈췄어요")
        edit { Drive(it.place, it.comfortable, vehicle = true, log = it.log.takeLast(1)) }
        AppState.vehicleNote = "차량 신호 연결됨"
    }

    // ---- vehicle ----

    override fun onGear(drive: Boolean, park: Boolean) {
        if (!vehicle) {
            vehicle = true
            AppState.vehicleNote = "차량 신호 연결됨"
            edit { it.copy(vehicle = true) }
            if (!drive && !started) log("기어 P · D로 넣으면 안내를 시작해요")
        }
        if (drive && !started) begin()
        if (park && started && !finished) {
            // A stale D right after connecting (e.g. the car was just parked) gives a drive that "ends" at once.
            // Treat P within 5 s of D as a false start and keep waiting for D.
            if (now() - startedAt < FALSE_START_MS) falseStart() else finish()
        }
    }

    override fun onTurnSignal(on: Boolean) {
        val open = signalSpans.lastOrNull()?.takeIf { it[1] == Long.MAX_VALUE }
        if (on && open == null) signalSpans += longArrayOf(now(), Long.MAX_VALUE)
        if (!on && open != null) open[1] = now()
        if (started && !finished && (on == (open == null))) log("방향지시등 ${if (on) "켬" else "끔"} · $src")
        android.util.Log.i("DriveWatch", "turn signal on=$on t=${now()}")
    }

    override fun onBrake(pressed: Boolean) {
        braking = pressed
        if (!pressed) countedThisPress = false
    }

    /**
     * Hard brake = speed falls by 4 m/s² or more while the brake is pressed; once per press (광채's rule).
     * Measured over about a second, not between consecutive samples (~0.1 s apart), so small steps
     * and sensor jitter do not read as hard braking.
     */
    override fun onSpeed(mps: Double) {
        val t = now()
        speeds.addLast(t to mps)
        while (speeds.size > 1 && t - speeds.first().first > 1_500) speeds.removeFirst()
        if (!started || finished || !braking || countedThisPress) return
        val prev = speeds.lastOrNull { t - it.first >= 800 } ?: return
        val dt = (t - prev.first) / 1000.0
        if (dt > 0 && (prev.second - mps) / dt >= HARD_BRAKE_MPS2) {
            countedThisPress = true
            edit { it.copy(hardBrakes = it.hardBrakes + 1) }
            log("급제동 · $src")
        }
    }

    // ---- drive ----

    private fun begin() {
        val d = AppState.drive ?: return stopSelf()
        started = true
        startedAt = now()
        edit { it.copy(started = true) }
        log("D단 · ${d.place}로 안내 시작 (${if (d.comfortable) "편한 길" else "빠른 길"})")
        // Navigation is scripted until NaviHelper is wired in; a 시연 재생 carries its own navigation events.
        if (!replaying) for ((at, event) in Demo.script(d.place, d.comfortable)) main.postDelayed({ step(event) }, at * 1000L)
    }

    private fun step(e: NavEvent) {
        if (finished) return
        when (e) {
            is NavEvent.Turn -> if (vehicle) {
                // Real turn signal: counts if it was on at any moment from 3 s before to 3 s after the guidance.
                val g = now()
                android.util.Log.i("DriveWatch", "turn guidance ${e.dir} t=$g")
                main.postDelayed({ turn(e, signalSpans.any { it[0] <= g + 3_000 && it[1] >= g - 3_000 }) }, 3_000)
            } else turn(e, e.signal)
            is NavEvent.Reroute -> {
                edit { it.copy(reroutes = it.reroutes + e.point) }
                log("경로 재탐색 · ${e.point} (시연용)")
                if ((AppState.drive?.reroutes?.size ?: 0) >= 2 && spoken < 2) { spoken++; say(DRIVING_LINE) }
            }
            NavEvent.HardBrake -> if (!vehicle) {
                edit { it.copy(hardBrakes = it.hardBrakes + 1) }
                log("급제동 (시연용)")
            }
            is NavEvent.Arrive -> {
                edit { it.copy(km = e.km) }
                log("목적지 도착 안내 (시연용)")
                if (vehicle) log("기어를 P로 넣으면 기록을 마쳐요") else main.postDelayed({ finish() }, 1_500)
            }
        }
    }

    private fun turn(e: NavEvent.Turn, used: Boolean) {
        if (finished) return
        edit { it.copy(turns = it.turns + 1, signals = it.signals + if (used) 1 else 0) }
        log("${e.dir} 안내 · ${e.point} (시연용) · 깜빡이 ${if (used) "켬" else "안 켬"}")
    }

    private fun falseStart() {
        main.removeCallbacksAndMessages(null)
        started = false
        spoken = 0
        edit { Drive(it.place, it.comfortable, vehicle = true, log = listOf("기어 P · D로 넣으면 안내를 시작해요")) }
    }

    private fun finish() {
        if (finished) return
        finished = true
        val d = AppState.drive ?: return stopSelf()
        val familiar = Demo.PLACES.any { it.first == d.place }
        val trip = Trip(0, d.km.takeIf { it > 0 } ?: 10.0, familiar, AppState.night, d.reroutes.size, d.turns, d.signals, d.hardBrakes, d.place)
        AppState.updateId(profileId) {
            it.copy(trips = it.trips + trip, avoidedThisWeek = it.avoidedThisWeek + if (d.comfortable && it.marked.any { m -> m.first == d.place }) 1 else 0)
        }
        val driver = AppState.byId(profileId) ?: return stopSelf()
        Backup.tripFinished(driver, trip)
        val report = analyze(driver.trips)
        val line = arrivalLine(d, report)
        edit { it.copy(finished = true, report = report) }
        log("P단 · 도착 · 기록 저장")
        AppState.screen = Screen.ARRIVED
        link?.stop(); link = null
        replay?.stop(); replay = null
        say(line) { main.post { stopSelf() } }
        main.postDelayed({ stopSelf() }, 30_000)
    }

    private fun say(line: String, done: (() -> Unit)? = null) {
        edit { it.copy(banner = line) }
        tts?.speak(line, done) ?: done?.invoke()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("DriveWatch")
            .setContentText("이번 주행을 기록하고 있어요")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        replay?.stop()
        link?.stop()
        tts?.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "drive"
        private const val HARD_BRAKE_MPS2 = 4.0
        private const val FALSE_START_MS = 5_000L
        private const val ACTION_REPLAY = "com.parkhere.drivewatch.REPLAY"

        /** 시연 재생 for the drive that is waiting for D (the Driving screen's button). */
        fun replay(context: Context) =
            context.startForegroundService(Intent(context, DriveService::class.java).setAction(ACTION_REPLAY))

        fun start(context: Context, place: String, comfortable: Boolean) {
            AppState.drive = Drive(place, comfortable)
            AppState.screen = Screen.DRIVE
            context.startForegroundService(Intent(context, DriveService::class.java))
        }
    }
}
