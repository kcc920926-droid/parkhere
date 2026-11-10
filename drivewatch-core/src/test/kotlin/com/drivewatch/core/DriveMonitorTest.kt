package com.drivewatch.core

import com.drivewatch.core.collect.CollectorConfig
import com.drivewatch.core.collect.DriveMonitor
import com.drivewatch.core.collect.MonitorUpdate
import com.drivewatch.core.demo.DemoDrive
import com.drivewatch.core.demo.DemoReplays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveMonitorTest {
    private val config = CollectorConfig.demo(windowMs = 8_000)

    private fun drive(brakes: List<Int>): List<MonitorUpdate> {
        val monitor = DriveMonitor(DemoReplays.history, config, context = "city")
        return DemoDrive.events(config.windowMs, brakes).flatMap(monitor::accept)
    }

    @Test
    fun `the recorded demo drive is counted per window and detected in the fifth window`() {
        val updates = drive(DemoDrive.hardBrakesPerWindow)
        val judged = updates.filterIsInstance<MonitorUpdate.WindowJudged>()
        assertEquals(listOf(1.0, 0.0, 1.0, 4.0, 5.0, 1.0), judged.map { it.frame.values[Signal.HARD_BRAKES] })
        assertEquals(
            listOf(Observation.TYPICAL, Observation.TYPICAL, Observation.TYPICAL, Observation.WATCHING, Observation.CHANGE, Observation.TYPICAL),
            judged.map { it.result.observation },
        )

        val report = (updates.last() as MonitorUpdate.Arrived).report
        val detection = assertNotNull(report.detection)
        assertEquals(5 * 8 / 60.0, detection.minute, 1e-9)
        assertEquals(listOf(Signal.HARD_BRAKES to 2), detection.evidence.map { it.signal to it.streak })
        assertEquals(3.0, report.baselines[Signal.HARD_BRAKES]?.upper)
        assertEquals(6, report.frames.size)
        // 평소 조향 범위(상한 6회) 안이고, 주행 시간도 평소 범위 안이다.
        assertTrue(judged.all { it.frame.values[Signal.STEERING_CORRECTIONS]!! <= 6.0 })
    }

    @Test
    fun `one window above the usual range is not a change`() {
        val report = (drive(listOf(1, 0, 4, 1, 1, 0)).last() as MonitorUpdate.Arrived).report
        assertNull(report.detection)
        assertEquals(Observation.WATCHING, report.results[2].observation)
    }

    @Test
    fun `the arrival report feeds the weekly trip summary`() {
        val report = (drive(DemoDrive.hardBrakesPerWindow).last() as MonitorUpdate.Arrived).report
        val trip = WeeklyEngine.tripFromFrames(report.frames, java.time.LocalDate.parse("2026-11-11"), interval = report.windowMinutes)
        assertEquals("city", trip.context)
        assertEquals(12.0, trip.values[Signal.HARD_BRAKES])
        assertTrue(trip.km > 0.3 && trip.km < 0.6, "48초를 시속 30km 남짓으로 달린 거리, 실제 ${trip.km}km")
    }
}
