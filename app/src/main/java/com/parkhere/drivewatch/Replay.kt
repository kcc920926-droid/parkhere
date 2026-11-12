package com.parkhere.drivewatch

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 시연 재생: a recorded drive played into the same path real vehicle signals take.
 *
 * A scenario (family-server/demo/drives/<id>.json, served at /api/demo/drives/{id} and bundled as an asset
 * for offline use) is a timeline of gear, speed, brake, turn signal and navigation events. [ReplayLink]
 * feeds it to a [VehicleLink.Listener] exactly as [VehicleLink] would, so DriveService, the hard-brake
 * rule, the turn-signal window and the arrival card run the code they run on a real car.
 */
data class Timeline(val id: String, val title: String, val place: String, val comfortable: Boolean, val events: List<Ev>) {
    sealed class Ev(val at: Double) {
        class Gear(at: Double, val drive: Boolean) : Ev(at)
        class Speed(at: Double, val kmh: Double) : Ev(at)
        class Brake(at: Double, val pressed: Boolean) : Ev(at)
        class Signal(at: Double, val on: Boolean) : Ev(at)
        class Nav(at: Double, val event: NavEvent) : Ev(at)
    }

    companion object {
        /** One JSON event can carry several values at the same moment (e.g. speed and brake). */
        fun parse(j: JSONObject): Timeline {
            val out = mutableListOf<Ev>()
            val a = j.getJSONArray("events")
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                val at = e.getDouble("at")
                if (e.has("gear")) out += Ev.Gear(at, e.getString("gear") == "D")
                if (e.has("brake")) out += Ev.Brake(at, e.getBoolean("brake"))
                if (e.has("speed")) out += Ev.Speed(at, e.getDouble("speed"))
                if (e.has("signal")) out += Ev.Signal(at, e.getBoolean("signal"))
                e.optJSONObject("turn")?.let { t -> out += Ev.Nav(at, NavEvent.Turn(t.getString("dir"), t.getString("point"), signal = false)) }
                if (e.has("reroute")) out += Ev.Nav(at, NavEvent.Reroute(e.getString("reroute")))
                if (e.has("arrive")) out += Ev.Nav(at, NavEvent.Arrive(e.getDouble("arrive")))
            }
            return Timeline(j.getString("id"), j.optString("title"), j.optString("place"), j.optBoolean("comfortable"), out.sortedBy { it.at })
        }
    }
}

/** Plays a [Timeline]. Speed is repeated every 100 ms like the car's speed sensor, so rate-based rules see a real signal. */
class ReplayLink(private val timeline: Timeline, private val listener: VehicleLink.Listener, private val nav: (NavEvent) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var kmh = 0.0
    private val tick = object : Runnable {
        override fun run() { listener.onSpeed(kmh / 3.6); main.postDelayed(this, 100) }
    }

    fun start() {
        for (e in timeline.events) main.postDelayed({
            when (e) {
                is Timeline.Ev.Gear -> listener.onGear(drive = e.drive, park = !e.drive)
                is Timeline.Ev.Speed -> { kmh = e.kmh; listener.onSpeed(kmh / 3.6) }
                is Timeline.Ev.Brake -> listener.onBrake(e.pressed)
                is Timeline.Ev.Signal -> listener.onTurnSignal(e.on)
                is Timeline.Ev.Nav -> nav(e.event)
            }
        }, (e.at * 1000).toLong())
        main.postDelayed(tick, 100)
    }

    fun stop() = main.removeCallbacksAndMessages(null)
}

/** Scenarios come from the family server, so they can change without a new APK; the bundled copy is the fallback. */
object Scenarios {
    private val main by lazy { Handler(Looper.getMainLooper()) } // lazy: unit tests use driveId() without Android
    private val ID = mapOf("병원" to "hospital", "성당" to "church", "마트" to "mart")

    fun driveId(place: String, comfortable: Boolean) = "${ID[place] ?: "hospital"}-${if (comfortable) "comfortable" else "fast"}"

    /** Calls back on the main thread with the JSON and where it came from ("서버" or "앱에 담긴 예비본"). */
    private fun load(context: Context, kind: String, id: String, done: (JSONObject?, String) -> Unit) {
        val app = context.applicationContext
        thread {
            val fromServer = runCatching {
                val c = URL("${FamilyApi.BASE}/api/demo/$kind/$id").openConnection() as HttpURLConnection
                c.connectTimeout = 3000; c.readTimeout = 3000
                if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().readText()) else null
            }.getOrNull()
            val json = fromServer ?: runCatching { JSONObject(app.assets.open("$kind/$id.json").bufferedReader().readText()) }.getOrNull()
            main.post { done(json, if (fromServer != null) "서버" else "앱에 담긴 예비본") }
        }
    }

    fun drive(context: Context, place: String, comfortable: Boolean, done: (Timeline?, String) -> Unit) =
        load(context, "drives", driveId(place, comfortable)) { j, from -> done(j?.let { runCatching { Timeline.parse(it) }.getOrNull() }, from) }

    /** Synthetic weeks for the demo, marked demo = true so they never mix with real drives or leave the car. */
    fun history(context: Context, id: String, done: (List<Trip>?, String) -> Unit) =
        load(context, "histories", id) { j, from ->
            done(j?.optJSONArray("trips")?.let { a ->
                (0 until a.length()).map { i ->
                    val t = a.getJSONObject(i)
                    Trip(t.getInt("weekAgo"), t.getDouble("km"), t.getBoolean("familiar"), t.optBoolean("night"), t.getInt("reroutes"),
                        t.getInt("turns"), t.getInt("signals"), t.getInt("hardBrakes"), t.optString("place"), demo = true)
                }
            }, from)
        }

    /** Order the 시연 패널 cycles through, with the label it shows. */
    val HISTORIES = listOf("steady" to "평소대로", "watch" to "관찰", "talk" to "대화 권장")
}
