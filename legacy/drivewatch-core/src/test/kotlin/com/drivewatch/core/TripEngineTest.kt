package com.drivewatch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** drive-watcher/engine.test.js 를 그대로 옮긴 테스트. */
class TripEngineTest {
    private val history = listOf("city", "highway").flatMap { context ->
        List(5) {
            HistoryFrame(
                context,
                mapOf(
                    Signal.HARD_BRAKES to if (context == "city") 1.0 else 4.0,
                    Signal.STEERING_CORRECTIONS to 2.0,
                    Signal.MINUTES_SINCE_BREAK to 15.0,
                ),
            )
        }
    }

    private fun frame(minute: Int) = Frame(
        minute = minute.toDouble(), context = "city", gear = Gear.D, speedKph = 40.0,
        values = mapOf(Signal.HARD_BRAKES to 5.0),
    )

    @Test
    fun `records the first sustained change with evidence and keeps it through parking`() {
        val session = TripEngine.createSession(history)
        assertEquals(Observation.WATCHING, session.ingest(frame(2)).observation)
        assertNull(session.ingest(frame(4).copy(values = mapOf(Signal.HARD_BRAKES to 3.0))).detection)
        assertNull(session.ingest(frame(6)).detection)
        val changed = session.ingest(frame(8))
        assertEquals(8.0, changed.detection?.minute)
        assertEquals(false, changed.parked)
        assertEquals(
            listOf(Triple(Signal.HARD_BRAKES, 5.0, 2)),
            changed.detection?.evidence?.map { Triple(it.signal, it.value, it.streak) },
        )

        assertEquals(false, session.ingest(frame(9).copy(gear = Gear.P)).parked)
        val parked = session.ingest(frame(10).copy(gear = Gear.P, speedKph = 0.0, values = emptyMap()))
        assertEquals(true, parked.parked)
        assertEquals(changed.detection, parked.detection)
    }

    @Test
    fun `a different road context, a gap, and missing signals break continuity`() {
        val session = TripEngine.createSession(history)
        session.ingest(frame(2))
        assertEquals(Observation.TYPICAL, session.ingest(frame(4).copy(context = "highway")).observation)
        assertNull(session.ingest(frame(6)).detection)
        assertNull(session.ingest(frame(10)).detection)
        assertEquals(Observation.INSUFFICIENT, session.ingest(frame(12).copy(values = emptyMap())).observation)
        assertNull(session.ingest(frame(14)).detection)
        assertEquals(16.0, session.ingest(frame(16)).detection?.minute)
    }

    @Test
    fun `without enough same-condition history, no change is inferred`() {
        val session = TripEngine.createSession(history.take(4))
        assertEquals(Observation.INSUFFICIENT, session.ingest(frame(2)).observation)
        assertNull(session.ingest(frame(4)).detection)
        assertFailsWith<IllegalArgumentException> { session.ingest(frame(4)) }
    }

    @Test
    fun `measured movement can be compared without inventing a gear or parked state`() {
        val session = TripEngine.createSession(history)
        val first = session.ingest(frame(2).copy(gear = null, driven = true))
        assertEquals(Observation.WATCHING, first.observation)
        assertEquals(false, first.parked)
        val second = session.ingest(frame(4).copy(gear = null, driven = true))
        assertEquals(Observation.CHANGE, second.observation)
        assertEquals(false, second.parked)
        assertEquals(false, session.ingest(frame(6).copy(gear = null, speedKph = 0.0)).parked)
    }
}
