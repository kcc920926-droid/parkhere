package com.drivewatch.core

import com.drivewatch.core.demo.DemoWeeklyHistory
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** drive-watcher/weekly.test.js 를 그대로 옮긴 테스트. */
class WeeklyEngineTest {
    private fun scene(id: String) = DemoWeeklyHistory.scenes.first { it.id == id }
    private fun weeks(id: String) = WeeklyEngine.assess(DemoWeeklyHistory.tripsFor(scene(id)), context = "city")
    private fun keys(week: Week) = week.evidence.map { it.metric.key to it.streak }

    @Test
    fun `weeks start on Monday`() {
        assertEquals(LocalDate.parse("2026-11-02"), WeeklyEngine.weekOf("2026-11-08"))
        assertEquals(LocalDate.parse("2026-11-02"), WeeklyEngine.weekOf("2026-11-02"))
    }

    @Test
    fun `the first 14 days only learn, and one bad week does not change the status`() {
        assertEquals(
            List(2) { WeeklyStatus.LEARNING } + List(10) { WeeklyStatus.STABLE },
            weeks("steady").map { it.status },
        )
    }

    @Test
    fun `a change that continues moves from observe to talk`() {
        val weeks = weeks("changing")
        assertEquals(
            listOf(WeeklyStatus.STABLE, WeeklyStatus.OBSERVE, WeeklyStatus.OBSERVE, WeeklyStatus.TALK),
            weeks.takeLast(4).map { it.status },
        )
        assertEquals(listOf("hardBrakesPer100km" to 4, "steeringPer100km" to 2), keys(weeks.last()))
    }

    /** 웹 MVP 테스트에는 없는 경우: 지표 하나만 4주 이어져도 대화 권장이 된다. */
    @Test
    fun `one signal alone reaches talk only in its fourth week`() {
        val changing = scene("changing")
        val brakesOnly = changing.copy(steeringCorrections = scene("steady").steeringCorrections)
        val weeks = WeeklyEngine.assess(DemoWeeklyHistory.tripsFor(brakesOnly), context = "city")
        assertEquals(
            listOf(WeeklyStatus.STABLE, WeeklyStatus.OBSERVE, WeeklyStatus.OBSERVE, WeeklyStatus.TALK),
            weeks.takeLast(4).map { it.status },
        )
        assertEquals(listOf("hardBrakesPer100km" to 4), keys(weeks.last()))
    }

    @Test
    fun `weeks above the line stay out of the baseline`() {
        val weeks = weeks("changing")
        fun upper(week: Week) =
            week.readings.first { it.metric == WeeklyMetric.HARD_BRAKES_PER_100KM }.baseline!!.upper
        assertEquals(upper(weeks[weeks.size - 4]), upper(weeks.last()))
    }

    @Test
    fun `a week with too few trips is not judged and breaks the streak`() {
        val from = LocalDate.parse("2026-10-19")
        val until = LocalDate.parse("2026-10-24")
        val thinned = DemoWeeklyHistory.tripsFor(scene("changing"))
            .filterNot { !it.date.isBefore(from) && it.date.isBefore(until) }
        val weeks = WeeklyEngine.assess(thinned, context = "city")
        assertEquals(
            listOf(WeeklyStatus.STABLE, WeeklyStatus.INSUFFICIENT, WeeklyStatus.STABLE, WeeklyStatus.TALK),
            weeks.takeLast(4).map { it.status },
        )
        assertEquals(listOf("hardBrakesPer100km" to 2, "steeringPer100km" to 2), keys(weeks.last()))
    }

    /** 시연 기록은 오늘 기준으로 만든다. 요일이 달라도 판정 결과가 같아야 한다. */
    @Test
    fun `history anchored to today gives the same statuses on any weekday`() {
        val fixed = weeks("changing").map { it.status }
        for (offset in 0L..6L) {
            val today = LocalDate.parse("2026-12-03").plusDays(offset)
            val trips = DemoWeeklyHistory.tripsFor(scene("changing"), DemoWeeklyHistory.firstMondayFor(today))
            assertTrue(trips.all { !it.date.isAfter(today) }, "no trip after $today")
            assertTrue(trips.last().date.isAfter(today.minusDays(7)), "last trip within a week of $today")
            assertEquals(fixed, WeeklyEngine.assess(trips, context = "city").map { it.status })
        }
    }

    /** 평소 8주만 과거로 두면 안정에서 시작하고, 시계를 4주 넘기면 대화 권장이 된다. */
    @Test
    fun `moving the clock four weeks ahead reveals the change`() {
        for (offset in 0L..6L) {
            val today = LocalDate.parse("2026-12-03").plusDays(offset)
            val trips = DemoWeeklyHistory.tripsFor(scene("changing"), DemoWeeklyHistory.firstMondayFor(today, completedWeeks = 8))
            fun statusAt(clock: LocalDate) =
                WeeklyEngine.assess(trips.filter { !it.date.isAfter(clock) }, context = "city")
                    .last { it.sampled }.status
            assertEquals(WeeklyStatus.STABLE, statusAt(today), "start on $today")
            assertEquals(WeeklyStatus.TALK, statusAt(today.plusWeeks(4)), "four weeks after $today")
        }
    }

    @Test
    fun `other road contexts keep their own baseline`() {
        val trips = DemoWeeklyHistory.tripsFor(scene("changing")).map { it.copy(context = "highway") }
        assertTrue(WeeklyEngine.assess(trips, context = "city").all { it.trips == 0 })
    }

    @Test
    fun `a trip summary is built from the trip engine's 2-minute frames`() {
        val frames = listOf(
            Frame(2.0, "city", Gear.D, 30.0, values = mapOf(Signal.HARD_BRAKES to 1.0, Signal.MINUTES_SINCE_BREAK to 2.0)),
            Frame(4.0, "city", Gear.D, 60.0, values = mapOf(Signal.HARD_BRAKES to 2.0, Signal.MINUTES_SINCE_BREAK to 4.0)),
            Frame(5.0, "city", Gear.P, 0.0),
        )
        assertEquals(
            Trip(
                date = LocalDate.parse("2026-11-08"), context = "city", km = 3.0,
                values = mapOf(
                    Signal.HARD_BRAKES to 3.0,
                    Signal.STEERING_CORRECTIONS to null,
                    Signal.MINUTES_SINCE_BREAK to 4.0,
                ),
            ),
            WeeklyEngine.tripFromFrames(frames, date = LocalDate.parse("2026-11-08")),
        )
    }
}
