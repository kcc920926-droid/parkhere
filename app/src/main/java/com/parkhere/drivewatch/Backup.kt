package com.parkhere.drivewatch

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Keeps drivers across app restarts. On the car: names, server tokens, onboarding, share scope and the
 * backup choice. On the server, only when the driver said yes: each drive's counts plus promises and marks.
 * Synthetic demo history is rebuilt locally, never uploaded.
 */
object Backup {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("drivewatch", Context.MODE_PRIVATE)
        restore()
    }

    /** Write the driver list and demo clock to the car. */
    fun persist() {
        val drivers = JSONArray()
        AppState.profiles.filter { it.name.isNotBlank() }.forEach { p ->
            drivers.put(JSONObject().put("id", p.id).put("name", p.name).put("token", p.carToken ?: JSONObject.NULL)
                .put("onboarded", p.onboarded).put("scope", p.shareScope)
                .put("backup", p.backup ?: JSONObject.NULL).put("promises", JSONArray(p.promises.toList())))
        }
        prefs?.edit()?.putString("drivers", drivers.toString())?.putInt("weekShift", AppState.weekShift)?.apply()
    }

    fun clear() { prefs?.edit()?.clear()?.apply() }

    /** Called by AppState.update for every profile change. */
    fun changed(old: Profile, new: Profile) {
        if (old.name != new.name || old.carToken != new.carToken || old.onboarded != new.onboarded ||
            old.shareScope != new.shareScope || old.backup != new.backup || old.promises != new.promises) persist()
        val token = new.carToken
        if (new.backup == true && token != null && (old.promises != new.promises || old.marked != new.marked)) FamilyApi.saveProfile(token, new)
    }

    fun tripFinished(p: Profile, trip: Trip) {
        val token = p.carToken ?: return
        if (p.backup == true) FamilyApi.saveTrip(token, AppState.week(), trip)
    }

    /** Synthetic history for the two demo drivers, shifted to the saved demo clock. */
    private fun demoHistory(name: String, weekShift: Int) =
        if (name == Demo.PARENT || name == "박영호") Demo.seed().map { it.copy(weekAgo = it.weekAgo + weekShift) } else emptyList()

    private fun restore() {
        val saved = prefs?.getString("drivers", null) ?: return
        val a = runCatching { JSONArray(saved) }.getOrNull() ?: return
        AppState.weekShift = prefs?.getInt("weekShift", 0) ?: 0
        val restored = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val name = o.getString("name")
            val onboarded = o.optBoolean("onboarded")
            Profile(
                name = name,
                trips = if (onboarded || name == "박영호") demoHistory(name, AppState.weekShift) else emptyList(),
                onboarded = onboarded,
                shareScope = o.optString("scope", "status"),
                promises = o.optJSONArray("promises")?.let { p -> (0 until p.length()).map { p.getInt(it) }.toSet() } ?: setOf(0, 1),
                carToken = o.optString("token").takeIf { it.isNotEmpty() && !o.isNull("token") },
                backup = if (o.isNull("backup")) null else o.optBoolean("backup"),
                id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() }, // older saves had no id
            )
        }
        if (restored.isEmpty()) return
        AppState.profiles.clear()
        AppState.profiles.addAll(restored)
        AppState.cur = restored.indexOfFirst { it.onboarded }.coerceAtLeast(0)
        AppState.screen = if (restored[AppState.cur].onboarded) Screen.HOME else Screen.SIGNUP
        restored.forEach { p -> if (p.backup == true && p.carToken != null) fetch(p.name, p.carToken) }
    }

    /** After the server handed back an existing driver that keeps drives: bring them back, as on a restart. */
    fun restoreFromServer(id: String) {
        val p = AppState.byId(id) ?: return
        val token = p.carToken ?: return
        fetch(p.name, token)
    }

    /** Bring back the drives and marks the server kept for this driver. */
    private fun fetch(name: String, token: String) = FamilyApi.backup(token) { j ->
        if (j == null) return@backup
        val now = LocalDate.parse(AppState.week())
        val trips = j.optJSONArray("trips") ?: JSONArray()
        val real = (0 until trips.length()).mapNotNull { k ->
            val t = trips.getJSONObject(k)
            val week = runCatching { LocalDate.parse(t.getString("week")) }.getOrNull() ?: return@mapNotNull null
            val weekAgo = ChronoUnit.WEEKS.between(week, now).toInt().takeIf { it >= 0 } ?: return@mapNotNull null
            Trip(weekAgo, t.optDouble("km", 10.0), familiar = Demo.PLACES.any { it.first == t.optString("place") },
                night = t.optBoolean("night"), reroutes = t.optInt("reroutes"), turnsGuided = t.optInt("turns"),
                signalsUsed = t.optInt("signals"), hardBrakes = t.optInt("hardBrakes"), place = t.optString("place"))
        }
        val profile = j.optJSONObject("profile")
        val marked = profile?.optJSONArray("marked")?.let { m -> (0 until m.length()).map { m.getJSONObject(it).let { x -> x.optString("place") to x.optString("point") } }.toSet() }
        val i = AppState.profiles.indexOfFirst { it.name == name && it.carToken == token }
        if (i < 0) return@backup
        // Direct assignment: restoring must not echo the same data back to the server.
        val p = AppState.profiles[i]
        AppState.profiles[i] = p.copy(trips = p.trips + real, marked = marked ?: p.marked)
    }
}
