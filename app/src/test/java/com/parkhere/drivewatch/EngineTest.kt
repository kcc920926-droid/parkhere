package com.parkhere.drivewatch

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineTest {
    private fun week(w: Int, reroutes: Int = 0, signals: Int = 10, brakes: Int = 1) =
        Trip(w, km = 40.0, familiar = true, night = false, reroutes = reroutes, turnsGuided = 10, signalsUsed = signals, hardBrakes = brakes)

    private fun history(recent: Map<Int, Trip>) = (0 until 12).map { recent[it] ?: week(it, reroutes = 1) }

    @Test fun stableWhenNothingChanges() =
        assertEquals(State.STABLE, analyze(history(emptyMap())).state)

    @Test fun learningUntilTwelveWeeks() =
        assertEquals(State.LEARNING, analyze((0 until 5).map { week(it) }).state)

    @Test fun oneBadWeekIsNotEnough() =
        assertEquals(State.STABLE, analyze(history(mapOf(0 to week(0, reroutes = 4)))).state)

    @Test fun twoBadWeeksIsWatch() {
        val r = analyze(history(mapOf(0 to week(0, reroutes = 4), 1 to week(1, reroutes = 4))))
        assertEquals(State.WATCH, r.state)
        assertEquals(listOf(Metric.REROUTE), r.changed.map { it.metric })
    }

    @Test fun twoMetricsIsTalk() {
        val bad = mapOf(0 to week(0, reroutes = 4, signals = 6), 1 to week(1, reroutes = 4, signals = 6))
        assertEquals(State.TALK, analyze(history(bad)).state)
    }

    /** Demo story: steady seed, then four weeks of re-routing on the 성당 route ("4주 뒤로") -> talk. */
    @Test fun demoSeedIsStableAndFourStrugglingWeeksTalk() {
        var trips = Demo.seed()
        assertEquals(State.STABLE, analyze(trips).state)
        repeat(4) { trips = trips.map { it.copy(weekAgo = it.weekAgo + 1) } + Demo.strugglingWeek() }
        val r = analyze(trips)
        assertEquals(State.TALK, r.state)
        assertEquals(listOf(Metric.REROUTE), r.changed.map { it.metric })
    }

    /** One bad drive this week (2 re-routes at the junction) is noticed on arrival but is not yet a change. */
    @Test fun oneBadDriveStaysStable() {
        val drive = Trip(0, 10.0, true, false, reroutes = 2, turnsGuided = 3, signalsUsed = 2, hardBrakes = 1, place = "병원")
        assertEquals(State.STABLE, analyze(Demo.seed() + drive).state)
    }

    @Test fun fourWeeksOfOneMetricIsTalk() {
        val bad = (0..3).associateWith { week(it, reroutes = 4) }
        assertEquals(State.TALK, analyze(history(bad)).state)
    }

    /** Marks belong to a destination; the weekly card names the marked points, wherever they are. */
    @Test fun weeklyCardNamesMarkedPoints() {
        val p = Profile("김정숙", Demo.seed(), marked = setOf("병원" to "중앙대로 합류"), avoidedThisWeek = 2)
        assertEquals("중앙대로 합류 2번 피했어요", weekly(p, analyze(p.trips)).avoided)
        assertEquals("", weekly(p.copy(marked = emptySet()), analyze(p.trips)).avoided)
    }

    /** Monthly view: each past week keeps the status it had then, and the message follows the current state. */
    @Test fun pastWeeksKeepTheirOwnStatus() {
        var trips = Demo.seed()
        repeat(4) { trips = trips.map { it.copy(weekAgo = it.weekAgo + 1) } + Demo.strugglingWeek() }
        val p = Profile("김정숙", trips)
        assertEquals(State.STABLE, reportAt(trips, 4).state) // four weeks ago, before the change
        assertEquals(State.WATCH, reportAt(trips, 2).state)  // two worse weeks by then
        assertEquals(State.TALK, reportAt(trips, 0).state)
        assertEquals("몇 주째 평소와 다른 모습이 이어지고 있어요", nextStep(p, analyze(trips)).title)
        assertEquals("지난 12주 동안 평소대로 잘 운전하고 계세요".substring(0, 3), nextStep(Profile("박영호", Demo.seed()), analyze(Demo.seed())).title.substring(0, 3))
    }
}
