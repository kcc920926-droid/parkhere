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

    /** A server driver: new, or the same one this car's driver had before (reused). backup = the choice kept on the server. */
    data class Registered(val token: String, val reused: Boolean, val backup: Boolean?)

    /**
     * Registers a driver of this car. With a car key the server hands back the existing driver for the same
     * local driver or name, so family links survive a reinstall. Logged with why, so a duplicate can be traced.
     */
    fun registerDriver(name: String, scope: String, reason: String, profileId: String, carKey: String?, done: (Registered?) -> Unit) {
        val body = JSONObject().put("name", name).put("shareScope", scope).put("profileId", profileId)
        carKey?.let { body.put("carKey", it) }
        call("POST", "/api/car/drivers", null, body) { j ->
            val r = j?.optString("carToken")?.takeIf { it.isNotEmpty() }?.let { Registered(it, j.optBoolean("reused"), if (j.isNull("backup")) null else j.optBoolean("backup")) }
            android.util.Log.i("DriveWatch", "registerDriver reason=$reason profile=${profileId.take(8)} carKey=${carKey != null} -> ${when { r == null -> "failed"; r.reused -> "reused"; else -> "new" }}")
            done(r)
        }
    }

    /** The newest app build the family server offers (tools/publish-apk.sh). */
    data class AppRelease(val versionCode: Int, val versionName: String, val notes: String, val download: String)

    /** Null when the server has no published build or cannot be reached; the app then says nothing. */
    fun latestApp(done: (AppRelease?) -> Unit) = call("GET", "/api/app/latest", null, null) { j ->
        done(j?.takeIf { it.has("versionCode") }?.let { AppRelease(it.getInt("versionCode"), it.optString("versionName"), it.optString("notes"), it.optString("download")) })
    }

    fun setShareScope(token: String, scope: String, done: (Boolean) -> Unit) =
        call("PUT", "/api/car/share-scope", token, JSONObject().put("shareScope", scope)) { done(it != null) }

    fun invite(token: String, done: (String?) -> Unit) =
        call("POST", "/api/car/invites", token, JSONObject()) { done(it?.optString("code")) }

    /** Who is linked, who acknowledged the latest summary, and the latest "가족에게 부탁" with its answer. */
    fun me(token: String, done: (family: List<String>, ackedBy: List<String>, week: String?, ride: RideRequest?) -> Unit) =
        call("GET", "/api/car/me", token, null) { j ->
            if (j == null) return@call
            val latest = j.optJSONObject("latest")
            done(names(j.optJSONArray("family")), names(latest?.optJSONArray("ackedBy")), latest?.optString("week"), ride(j.optJSONObject("request")))
        }

    /**
     * "가족에게 부탁": the driver chose not to drive today. Only the place name goes (a 생활길 place), never a location.
     * A linked family member answers on the phone page: pickup (태우러 가기) or taxi (택시를 대신 불러 드리기).
     */
    data class RideRequest(val id: String, val place: String, val answer: String?, val answeredBy: String?)

    private fun ride(j: JSONObject?) = j?.optString("requestId")?.takeIf { it.isNotEmpty() }?.let {
        RideRequest(it, j.optString("place"), j.optString("answer").takeIf { a -> !j.isNull("answer") && a.isNotEmpty() },
            j.optString("answeredBy").takeIf { b -> !j.isNull("answeredBy") && b.isNotEmpty() })
    }

    fun askFamily(token: String, place: String, done: (RideRequest?) -> Unit) =
        call("POST", "/api/car/requests", token, JSONObject().put("place", place)) { done(ride(it)) }

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

    /** Result of texting the invite link: sent, or not sent because the server is in demo mode, or an error code. */
    data class SmsResult(val sent: Boolean, val dryRun: Boolean, val to: String?, val error: String?)

    /** Texts the phone page link for [code] to the child's number. The server keeps neither the number nor the text. */
    fun sendInviteSms(token: String, code: String, phone: String, consent: Boolean, done: (SmsResult) -> Unit) {
        thread {
            val result = runCatching {
                val c = URL("$BASE/api/car/invites/sms").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000; c.readTimeout = 12000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer $token")
                c.doOutput = true
                c.outputStream.use { it.write(JSONObject().put("code", code).put("phone", phone).put("consent", consent).toString().toByteArray()) }
                val ok = c.responseCode in 200..299
                val body = JSONObject(((if (ok) c.inputStream else c.errorStream) ?: return@runCatching SmsResult(false, false, null, "network")).bufferedReader().readText())
                if (ok) SmsResult(body.optBoolean("sent"), body.optString("mode") == "dry-run", body.optString("to"), null)
                else SmsResult(false, false, null, body.optString("error", "server_error"))
            }.getOrElse { SmsResult(false, false, null, "network") }
            main.post { done(result) }
        }
    }

    /**
     * 시연용: plays the child's side on the same server so the demo does not need a second phone.
     * Signs up a family member named "$name (시연용)" and enters [code], exactly as the phone page would.
     */
    fun demoChildLinks(code: String, name: String, relation: String, done: (Boolean) -> Unit) =
        call("POST", "/api/family/members", null, JSONObject().put("name", "$name (시연용)")) { m ->
            val token = m?.optString("token")?.takeIf { it.isNotEmpty() } ?: return@call done(false)
            call("POST", "/api/family/links", token, JSONObject().put("code", code).put("relation", relation)) { done(it != null) }
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
