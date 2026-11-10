package com.drivewatch.core

import com.drivewatch.core.collect.BrakeState
import com.drivewatch.core.collect.CollectorConfig
import com.drivewatch.core.collect.CollectorOutput
import com.drivewatch.core.collect.GearState
import com.drivewatch.core.collect.SignalEvent
import com.drivewatch.core.collect.SpeedSample
import com.drivewatch.core.collect.SteeringSample
import com.drivewatch.core.collect.WindowCollector
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectorTest {
    private val config = CollectorConfig.demo(windowMs = 8_000)

    /** 신호를 넣고, 끝난 구간과 주행 끝을 모아 돌려준다. */
    private fun run(events: List<SignalEvent>, collector: WindowCollector = WindowCollector(config)): List<CollectorOutput> =
        events.sortedBy { it.timeMs }.flatMap(collector::accept)

    private fun frames(outputs: List<CollectorOutput>) =
        outputs.filterIsInstance<CollectorOutput.WindowClosed>().map { it.frame }

    /** 10Hz 차속. speedAt이 그 시각의 m/s를 정한다. */
    private fun speeds(untilMs: Long, speedAt: (Long) -> Double) =
        (0..untilMs step 100).map { SpeedSample(it, speedAt(it)) }

    private fun brakes(frame: Frame) = frame.values[Signal.HARD_BRAKES]

    @Test
    fun `a hard brake needs the pedal and a fast enough drop in speed`() {
        // 2초부터 1초 동안 초당 5m/s씩 줄어든다.
        val drop = speeds(8_000) { if (it < 2_000) 15.0 else if (it < 3_000) 15.0 - (it - 2_000) * 0.005 else 10.0 }
        val start = GearState(0, Gear.D)

        val pressed = run(listOf(start, BrakeState(1_900, true), BrakeState(3_100, false)) + drop)
        assertEquals(1.0, brakes(frames(pressed).single()))

        val notPressed = run(listOf(start, BrakeState(0, false)) + drop)
        assertEquals(0.0, brakes(frames(notPressed).single()), "감속만 있고 브레이크가 안 눌렸으면 세지 않는다")

        val gentle = speeds(8_000) { if (it < 2_000) 15.0 else if (it < 4_000) 15.0 - (it - 2_000) * 0.002 else 11.0 }
        assertEquals(0.0, brakes(frames(run(listOf(start, BrakeState(1_900, true)) + gentle)).single()), "초당 2m/s 감속은 강한 제동이 아니다")
    }

    @Test
    fun `holding the pedal counts once, and pressing again counts again`() {
        // 2초와 5초에 각각 0.8초 동안 초당 6m/s씩 줄어든다.
        val twoDrops = speeds(8_000) {
            when {
                it in 2_000 until 2_800 -> 15.0 - (it - 2_000) * 0.006
                it in 2_800 until 5_000 -> 10.2
                it in 5_000 until 5_800 -> 10.2 - (it - 5_000) * 0.006
                it >= 5_800 -> 5.4
                else -> 15.0
            }
        }
        val start = GearState(0, Gear.D)
        val held = run(listOf(start, BrakeState(1_900, true)) + twoDrops)
        assertEquals(1.0, brakes(frames(held).single()), "브레이크를 떼지 않으면 한 번")
        val released = run(listOf(start, BrakeState(1_900, true), BrakeState(3_000, false), BrakeState(4_900, true)) + twoDrops)
        assertEquals(2.0, brakes(frames(released).single()))
    }

    @Test
    fun `a sudden step in speed, as the Virtual Console sends it, is one hard brake`() {
        // 콘솔은 50km/h에서 35km/h로 한 번에 바꾼다. 차속은 0.14초마다 온다.
        val samples = (0..8_400L step 140).map { SpeedSample(it, if (it < 3_000) 50 / 3.6 else 35 / 3.6) }
        val out = run(listOf(GearState(0, Gear.D), BrakeState(2_990, true), BrakeState(4_000, false)) + samples)
        assertEquals(1.0, brakes(frames(out).single()))
    }

    @Test
    fun `without a brake signal, deceleration alone is used and must ease off between counts`() {
        val twoDrops = speeds(8_000) {
            when {
                it in 2_000 until 2_800 -> 15.0 - (it - 2_000) * 0.006
                it in 2_800 until 5_000 -> 10.2
                it in 5_000 until 5_800 -> 10.2 - (it - 5_000) * 0.006
                it >= 5_800 -> 5.4
                else -> 15.0
            }
        }
        assertEquals(2.0, brakes(frames(run(listOf(GearState(0, Gear.D)) + twoDrops)).single()))
    }

    @Test
    fun `steering corrections are reversals of at least the gap, counted only while moving`() {
        fun angles(amplitude: Double) = (0..8_000L step 100).map {
            // 2초 주기로 좌우를 오간다: 8초 동안 방향을 일곱 번 바꾼다.
            val phase = (it % 2_000) / 2_000.0
            SteeringSample(it, amplitude * if (phase < 0.5) 4 * phase - 1 else 3 - 4 * phase)
        }
        val moving = listOf(GearState(0, Gear.D)) + speeds(8_000) { 10.0 }
        fun corrections(events: List<SignalEvent>) = frames(run(events)).single().values[Signal.STEERING_CORRECTIONS]

        assertEquals(7.0, corrections(moving + angles(6.0)))
        assertEquals(0.0, corrections(moving + angles(2.0)), "좌우 2도는 보정으로 세지 않는다")
        val crawling = listOf(GearState(0, Gear.D)) + speeds(8_000) { 1.0 }
        assertEquals(0.0, corrections(crawling + angles(6.0)), "주차 조작 속도에서는 세지 않는다")
        assertNull(corrections(moving), "조향 신호가 없으면 0이 아니라 값 없음")
    }

    @Test
    fun `windows close on the boundary with average speed and time since the last break`() {
        val events = listOf(GearState(0, Gear.D), BrakeState(0, false)) + speeds(24_500) { 10.0 }
        val closed = frames(run(events))
        assertEquals(listOf(8.0 / 60, 16.0 / 60, 24.0 / 60), closed.map { it.minute })
        closed.forEach { frame ->
            assertEquals(Gear.D, frame.gear)
            assertEquals(true, frame.driven)
            assertTrue(abs(frame.speedKph!! - 36.0) < 0.5, "평균 36km/h, 실제 ${frame.speedKph}")
            assertEquals(0.0, brakes(frame))
        }
        assertTrue(abs(closed.last().values[Signal.MINUTES_SINCE_BREAK]!! - 0.4) < 0.01)
    }

    @Test
    fun `a window with no speed samples has no values instead of zeros`() {
        val collector = WindowCollector(config)
        val out = run(listOf(GearState(0, Gear.D)) + speeds(7_900) { 10.0 }, collector) + collector.tick(16_000)
        val second = frames(out)[1]
        assertNull(second.speedKph)
        assertNull(second.driven)
        assertNull(brakes(second))
    }

    @Test
    fun `a trip starts in D and ends when parked, dropping the unfinished window`() {
        val events = listOf(GearState(0, Gear.P), SpeedSample(0, 0.0), GearState(5_000, Gear.D)) +
            (5_000..18_000L step 100).map { SpeedSample(it, if (it < 17_000) 10.0 else 0.0) } +
            listOf(GearState(18_000, Gear.P))
        val out = run(events)
        assertEquals(CollectorOutput.TripStarted(5_000), out.first())
        assertEquals(listOf(8.0 / 60), frames(out).map { it.minute }, "5초에 출발했으니 13초에 첫 구간이 끝난다")
        val ended = out.last() as CollectorOutput.TripEnded
        assertEquals(Gear.P, ended.frame.gear)
        assertEquals(0.0, ended.frame.speedKph)
        assertTrue(ended.frame.minute > 8.0 / 60 && ended.frame.values.isEmpty())
    }

    @Test
    fun `with no gear signal a trip starts when the car moves`() {
        val out = run(speeds(9_500) { if (it < 1_000) 0.0 else 10.0 })
        assertEquals(CollectorOutput.TripStarted(1_000), out.first())
        val frame = frames(out).single()
        assertNull(frame.gear)
        assertEquals(true, frame.driven)
    }
}
