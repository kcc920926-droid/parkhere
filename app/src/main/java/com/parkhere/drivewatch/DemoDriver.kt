package com.parkhere.drivewatch

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 시연용: drives the Pleos emulator through its Virtual Console (http://127.0.0.1:8088 inside the
 * emulator), the same steps as tools/demo-drive.py. The app then receives these as real vehicle
 * signals (gear, turn signal, brake, speed). Only works on the Pleos Connect Emulator.
 */
object DemoDriver {
    private const val BASE = "http://127.0.0.1:8088"
    @Volatile var running = false
        private set

    private fun call(method: String, path: String) = runCatching {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 3000; c.readTimeout = 3000
        c.responseCode.also { c.disconnect() }
    }.onFailure { Log.w("DriveWatch", "vconsole $path: ${it.message}") }.getOrNull()

    private fun ctl(speed: Int, acc: Int = 0, brake: Int = 0) =
        call("PUT", "/vehicle/controls?speed=$speed&accelerator=$acc&brake=$brake&steering=0")
    private fun gear(g: String) = call("PUT", "/vehicle/gear?gear=$g")
    private fun signal(mode: String) = call("PUT", "/vehicle/turn-signal?mode=$mode")

    /** Runs one scripted drive in the background. Fast route: signals at 2 s and 15 s, none at 5 s, one hard brake. */
    fun drive(comfortable: Boolean, onDone: (Boolean) -> Unit = {}) {
        if (running) return
        running = true
        thread(name = "demo-drive") {
            var alive = true
            // Virtual Console only accepts changes while a "browser" keeps saying it is there.
            val beat = thread(name = "vconsole-beat") {
                while (alive) { call("POST", "/browser/session?client=drivewatch-app"); Thread.sleep(1000) }
            }
            val ok = runCatching {
                Thread.sleep(3000) // the console needs a few heartbeats before it accepts input
                val t0 = System.currentTimeMillis()
                fun at(sec: Double, step: () -> Any?) {
                    val wait = t0 + (sec * 1000).toLong() - System.currentTimeMillis()
                    if (wait > 0) Thread.sleep(wait)
                    step()
                }
                ctl(0, brake = 40); gear("Drive")
                at(0.5) { ctl(40, 30) }
                val end = if (comfortable) {
                    for (g in listOf(2.0, 6.0, 10.0)) {
                        at(g - 1.2) { signal("BlinkRight") }
                        at(g + 0.8) { signal("Off") }
                    }
                    13.5
                } else {
                    at(0.6) { signal("BlinkRight") }
                    at(1.3) { signal("Off") } // off 0.7 s before the 5 s turn's window opens (2 s)
                    at(4.0) { ctl(50, 30) }
                    at(12.0) { ctl(50, brake = 60) }
                    at(12.6) { ctl(20, brake = 60) } // 50 -> 20 km/h while braking = hard brake
                    at(13.2) { ctl(30, 20) }
                    at(13.5) { signal("BlinkRight") }
                    at(16.0) { signal("Off") }
                    18.5
                }
                // Gentle stop in 2 km/h steps (about 2.2 m/s²): big steps read as hard braking.
                val from = if (comfortable) 40 else 30
                val steps = from / 2
                for (i in 1..steps) at(end + i * 0.25) { ctl(from - 2 * i, brake = 30) }
                val stopped = end + steps * 0.25
                at(stopped + 1) { gear("Park") }
                at(stopped + 2) { ctl(0) }
            }.isSuccess
            alive = false
            beat.join(2000)
            running = false
            onDone(ok)
        }
    }
}
