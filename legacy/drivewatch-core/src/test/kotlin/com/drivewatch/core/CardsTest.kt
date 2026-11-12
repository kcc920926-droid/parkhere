package com.drivewatch.core

import com.drivewatch.core.card.Cards
import com.drivewatch.core.collect.CollectorConfig
import com.drivewatch.core.collect.DriveMonitor
import com.drivewatch.core.collect.MonitorUpdate
import com.drivewatch.core.demo.DemoDrive
import com.drivewatch.core.demo.DemoReplays
import com.drivewatch.core.demo.DemoWeeklyHistory
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardsTest {
    private fun report(config: CollectorConfig, brakes: List<Int>) = run {
        val monitor = DriveMonitor(DemoReplays.history, config, context = "city")
        val updates = DemoDrive.events(config.windowMs, brakes).flatMap(monitor::accept)
        (updates.last() as MonitorUpdate.Arrived).report
    }

    @Test
    fun `arrival card states the usual range, the worst window, and where`() {
        val card = Cards.arrival(report(CollectorConfig.demo(8_000), DemoDrive.hardBrakesPerWindow))
        assertEquals(true, card.changed)
        assertEquals("평소와 달라요", card.title)
        assertEquals("강한 제동 · 8초 구간당 평소 3회 이하, 이번 최고 5회", card.evidence)
        assertEquals("출발 후 24–32초 4회, 32–40초 5회", card.where)
        assertEquals(listOf(false, false, false, true, true, false), card.windows.map { it.above })
        assertEquals("0–8초", card.windows.first().label)
    }

    @Test
    fun `arrival card uses minutes for product-length windows`() {
        // 구간을 2분으로 두면 문구도 분 단위가 된다. 웹 MVP와 기획서의 문구와 같다.
        val card = Cards.arrival(report(CollectorConfig(windowMs = 120_000), DemoDrive.hardBrakesPerWindow))
        assertEquals("강한 제동 · 2분 구간당 평소 3회 이하, 이번 최고 5회", card.evidence)
        assertEquals("출발 후 6–8분 4회, 8–10분 5회", card.where)
    }

    @Test
    fun `a trip within the usual range gets a calm card`() {
        val card = Cards.arrival(report(CollectorConfig.demo(8_000), listOf(1, 0, 1, 2, 1, 0)))
        assertEquals(false, card.changed)
        assertEquals("평소와 비슷했어요", card.title)
        assertNull(card.evidence)
    }

    private val today = LocalDate.parse("2026-12-03")
    private fun weeklyAt(clock: LocalDate, sceneId: String = "changing") = run {
        val scene = DemoWeeklyHistory.scenes.first { it.id == sceneId }
        val trips = DemoWeeklyHistory.tripsFor(scene, DemoWeeklyHistory.firstMondayFor(today, completedWeeks = 8))
        Cards.weekly(WeeklyEngine.assess(trips.filter { !it.date.isAfter(clock) }, context = "city"))!!
    }

    @Test
    fun `weekly card moves from stable to observe to talk as the clock moves`() {
        val start = weeklyAt(today)
        assertEquals("안정", start.chip)
        assertEquals("이번 주도 평소처럼 운전하셨어요", start.headline)
        assertEquals(emptyList(), start.evidence)

        val watching = weeklyAt(today.plusWeeks(2))
        assertEquals("관찰", watching.chip)
        assertEquals("강한 제동이 2주째 평소보다 많았어요", watching.headline)
        assertEquals("강한 제동 · 100km당 6.3회 → 15.6회 · 2주 연속", watching.evidence.single().text)
        assertNull(watching.ask)

        val talk = weeklyAt(today.plusWeeks(4))
        assertEquals("대화 권장", talk.chip)
        assertEquals("강한 제동이 4주째, 조향 보정이 2주째 평소보다 많았어요", talk.headline)
        assertEquals(
            listOf("강한 제동 · 100km당 6.3회 → 21.9회 · 4주 연속", "조향 보정 · 100km당 37.5회 → 59.4회 · 2주 연속"),
            talk.evidence.map { it.text },
        )
        assertEquals("가족과 이야기해 보실래요?", talk.ask)
        assertEquals(listOf("쉬지 않고 운전한 시간은 평소 범위였어요"), talk.goodPoints)
        assertEquals(12, talk.strip.size)
        assertEquals(WeeklyStatus.LEARNING, talk.strip.first())
    }

    @Test
    fun `a driver whose weeks stay usual keeps a stable card`() {
        assertEquals("안정", weeklyAt(today.plusWeeks(4), "steady").chip)
    }

    @Test
    fun `no trips means no weekly card`() {
        assertNull(Cards.weekly(emptyList()))
    }
}
