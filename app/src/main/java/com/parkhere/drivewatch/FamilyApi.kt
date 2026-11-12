package com.parkhere.drivewatch

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Family summary server (family-server/, deployed at drive.olvend.com). The child reads summaries
 * in a phone browser at [FAMILY_PAGE]. Only the fields below leave the car; never location or route.
 */
object FamilyApi {
    val BASE: String = BuildConfig.FAMILY_SERVER
    val FAMILY_PAGE = BASE.substringAfter("://") + "/family"
    private val main = Handler(Looper.getMainLooper())

    private fun call(method: String, path: String, token: String?, body: JSONObject?, done: (JSONObject?) -> Unit) {
        thread {
            val result = runCatching {
                val c = URL(BASE + path).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Content-Type", "application/json")
                token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                body?.let { c.doOutput = true; c.outputStream.use { o -> o.write(it.toString().toByteArray()) } }
                if (c.responseCode !in 200..299) null else JSONObject(c.inputStream.bufferedReader().readText())
            }.getOrNull()
            main.post { done(result) }
        }
    }

    fun registerDriver(name: String, scope: String, done: (String?) -> Unit) =
        call("POST", "/api/car/drivers", null, JSONObject().put("name", name).put("shareScope", scope)) { done(it?.optString("carToken")) }

    fun invite(token: String, done: (String?) -> Unit) =
        call("POST", "/api/car/invites", token, JSONObject()) { done(it?.optString("code")) }

    /** Who is linked, and who acknowledged the latest summary. */
    fun me(token: String, done: (family: List<String>, ackedBy: List<String>, week: String?) -> Unit) =
        call("GET", "/api/car/me", token, null) { j ->
            if (j == null) return@call
            val latest = j.optJSONObject("latest")
            done(names(j.optJSONArray("family")), names(latest?.optJSONArray("ackedBy")), latest?.optString("week"))
        }

    fun sendSummary(token: String, week: String, w: Weekly, done: (Boolean) -> Unit) {
        val evidence = JSONArray()
        w.evidence.forEach { m ->
            val signal = m.metric == Metric.SIGNAL // stored as a miss rate, shown as compliance
            fun v(x: Double?) = x?.let { if (signal) 100 - it else it }
            evidence.put(JSONObject()
                .put("label", m.metric.label)
                .put("unit", when (m.metric) { Metric.REROUTE -> "회/10km"; Metric.SIGNAL -> "%"; Metric.BRAKE -> "회/100km" })
                .put("typical", v(m.baseline)).put("value", v(m.latest))
                .put("weeks", m.worseStreak))
        }
        val body = JSONObject().put("week", week).put("status", w.state.key()).put("headline", w.headline)
            .put("goodPoints", JSONArray(w.goodPoints)).put("promise", w.promise).put("avoided", w.avoided)
            .put("changes", JSONArray(w.changes)).put("evidence", evidence)
        call("POST", "/api/car/summaries", token, body) { done(it != null) }
    }

    // ---- 주행 기록 보관 (운전자가 허락한 경우만, family-server 0.2.0) ----

    fun setBackup(token: String, enabled: Boolean, done: (Boolean) -> Unit) =
        call("PUT", "/api/car/backup", token, JSONObject().put("enabled", enabled)) { done(it != null) }

    fun backup(token: String, done: (JSONObject?) -> Unit) = call("GET", "/api/car/backup", token, null, done)

    /** Counts only: no location, route or time of day leaves the car. */
    fun saveTrip(token: String, week: String, t: Trip) = call(
        "POST", "/api/car/trips", token,
        JSONObject().put("week", week).put("place", t.place).put("km", t.km).put("night", t.night)
            .put("reroutes", t.reroutes).put("turns", t.turnsGuided).put("signals", t.signalsUsed).put("hardBrakes", t.hardBrakes),
    ) {}

    fun saveProfile(token: String, p: Profile) = call(
        "PUT", "/api/car/profile", token,
        JSONObject().put("promises", JSONArray(p.promises.toList()))
            .put("marked", JSONArray(p.marked.map { (place, point) -> JSONObject().put("place", place).put("point", point) })),
    ) {}

    private fun names(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.optJSONObject(it)?.optString("name") ?: a.optString(it) }
}
