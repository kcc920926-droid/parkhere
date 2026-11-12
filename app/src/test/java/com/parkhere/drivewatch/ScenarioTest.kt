package com.parkhere.drivewatch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The server's demo scenarios (family-server/demo) must give the states the demo panel promises. */
class ScenarioTest {
    private val dir = File("../family-server/demo")

    private fun history(id: String): List<Trip> {
        val a = JSONObject(File(dir, "histories/$id.json").readText()).getJSONArray("trips")
        return (0 until a.length()).map { i ->
            val t = a.getJSONObject(i)
            Trip(t.getInt("weekAgo"), t.getDouble("km"), t.getBoolean("familiar"), t.optBoolean("night"), t.getInt("reroutes"),
                t.getInt("turns"), t.getInt("signals"), t.getInt("hardBrakes"), t.optString("place"), demo = true)
        }
    }

    @Test fun steadyHistoryIsStable() = assertEquals(State.STABLE, analyze(history("steady")).state)

    @Test fun watchHistoryIsWatchOnReroutes() {
        val r = analyze(history("watch"))
        assertEquals(State.WATCH, r.state)
        assertEquals(listOf(Metric.REROUTE), r.changed.map { it.metric })
    }

    @Test fun talkHistoryIsTalkAfterFourWeeks() {
        val r = analyze(history("talk"))
        assertEquals(State.TALK, r.state)
        assertEquals(4, r.metrics.first { it.metric == Metric.REROUTE }.worseStreak)
    }

    /** Every drive timeline is parsed by the app and ends parked after arriving. */
    @Test fun driveTimelinesParseAndEndInPark() {
        val files = File(dir, "drives").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertEquals(6, files.size)
        for (f in files) {
            val t = Timeline.parse(JSONObject(f.readText()))
            val gears = t.events.filterIsInstance<Timeline.Ev.Gear>()
            assertEquals(f.name, listOf(true, false), gears.map { it.drive })
            val arrive = t.events.first { it is Timeline.Ev.Nav && it.event is NavEvent.Arrive }
            assertTrue(f.name, arrive.at < gears.last().at)
            assertEquals(f.name, Scenarios.driveId(t.place, t.comfortable), t.id)
        }
    }
}
