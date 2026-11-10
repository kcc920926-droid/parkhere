package com.drivewatch.core

import com.drivewatch.core.demo.DemoReplays
import com.drivewatch.core.demo.DemoWeeklyHistory
import java.time.LocalDate
import java.util.Locale

/** tools/dump-parity.js 와 같은 형식·같은 난수 순서로 판정 결과를 뽑는다. */
object ParityDump {
    private fun num(value: Double?) = value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "-"

    private fun frameLine(tag: String, frame: Frame, result: FrameResult): String {
        val readings = result.readings.joinToString(" ") {
            "${it.signal.key}:${num(it.value)}:${num(it.baseline?.upper)}:${it.above}:${it.streak}"
        }
        val detection = result.detection?.let { d ->
            "${num(d.minute)}/${d.context}/${d.evidence.joinToString(",") { "${it.signal.key}x${it.streak}" }}"
        } ?: "-"
        return "$tag ${num(frame.minute)} ${result.observation.key} moving=${result.moving} parked=${result.parked} detection=$detection | $readings"
    }

    private fun weekLine(tag: String, week: Week): String {
        val readings = week.readings.joinToString(" ") {
            "${it.metric.key}:${num(it.value)}:${num(it.baseline?.typical)}:${num(it.baseline?.upper)}:${it.baseline?.weeks ?: "-"}:${it.compared}:${it.above}:${it.streak}"
        }
        val evidence = week.evidence.joinToString(",") { it.metric.key }.ifEmpty { "-" }
        return "$tag ${week.week} ${week.status.key} trips=${week.trips} km=${num(week.km)} soFar=${week.tripsSoFar} sampled=${week.sampled} evidence=$evidence | $readings"
    }

    private class Rng(seed: Long) {
        private var state = seed
        fun pick(n: Int): Int {
            state = state * 48271 % 2147483647
            return (state % n).toInt()
        }
    }

    fun lines(): List<String> {
        val lines = mutableListOf<String>()

        for (context in listOf("city", "highway")) {
            val baseline = TripEngine.baselineFor(DemoReplays.history, context)
            for (signal in Signal.entries) {
                val b = baseline[signal]
                lines += "baseline $context ${signal.key} ${num(b?.typical)} ${num(b?.upper)} ${b?.count ?: "-"}"
            }
        }
        for (scene in DemoReplays.scenes) {
            for (variant in DemoReplays.Variant.entries) {
                val tag = "${scene.id}/${variant.name.lowercase()}"
                val session = TripEngine.createSession(DemoReplays.history)
                val frames = DemoReplays.framesFor(scene, variant)
                for (frame in frames) lines += frameLine("frame $tag", frame, session.ingest(frame))
                val trip = WeeklyEngine.tripFromFrames(frames, LocalDate.parse("2026-11-08"))
                lines += "trip $tag ${trip.context} ${num(trip.km)} ${num(trip.values[Signal.HARD_BRAKES])} " +
                    "${num(trip.values[Signal.STEERING_CORRECTIONS])} ${num(trip.values[Signal.MINUTES_SINCE_BREAK])}"
            }
        }
        for (scene in DemoWeeklyHistory.scenes) {
            for (week in WeeklyEngine.assess(DemoWeeklyHistory.tripsFor(scene), context = "city")) {
                lines += weekLine("week ${scene.id}", week)
            }
        }

        run {
            val rng = Rng(20261110)
            val history = List(40) {
                val context = if (rng.pick(2) == 0) "city" else "highway"
                val values = mutableMapOf<Signal, Double?>()
                for (signal in Signal.entries) if (rng.pick(10) != 0) values[signal] = rng.pick(8).toDouble()
                HistoryFrame(context, values)
            }
            val session = TripEngine.createSession(history)
            var minute = 0
            var context = "city"
            for (i in 0 until 300) {
                val step = rng.pick(20)
                minute += if (step < 17) 2 else if (step < 19) 4 else 1
                if (rng.pick(25) == 0) context = if (context == "city") "highway" else "city"
                val g = rng.pick(20)
                val gear = if (g < 17) Gear.D else if (g == 17) Gear.P else if (g == 18) Gear.N else null
                val s = rng.pick(20)
                val speedKph = if (s == 0) null else if (s == 1) 0.0 else (1 + rng.pick(100)).toDouble()
                val d = rng.pick(16)
                val driven = if (d == 0) true else if (d == 1) false else null
                val values = mutableMapOf<Signal, Double?>()
                for (signal in Signal.entries) {
                    val p = rng.pick(20)
                    if (p == 1) values[signal] = -1.0 else if (p != 0) values[signal] = rng.pick(12).toDouble()
                }
                val frame = Frame(minute.toDouble(), context, gear, speedKph, driven, values)
                lines += frameLine("rframe $i", frame, session.ingest(frame))
            }
        }

        run {
            val rng = Rng(777)
            val ranges = listOf(6, 30, 90)
            val bumps = listOf(4, 20, 40)
            val start = LocalDate.of(2026, 3, 2)
            var day = 0
            val trips = List(150) { i ->
                day += rng.pick(3)
                val context = if (rng.pick(5) == 0) "highway" else "city"
                val km = rng.pick(201) / 10.0
                val values = mutableMapOf<Signal, Double?>()
                Signal.entries.forEachIndexed { index, signal ->
                    if (rng.pick(10) != 0) values[signal] = (rng.pick(ranges[index]) + if (i >= 90) bumps[index] else 0).toDouble()
                }
                Trip(start.plusDays(day.toLong()), context, km, values)
            }
            for (week in WeeklyEngine.assess(trips, context = "city")) lines += weekLine("rweek city", week)
            for (week in WeeklyEngine.assess(trips)) lines += weekLine("rweek all", week)
        }
        return lines
    }
}
