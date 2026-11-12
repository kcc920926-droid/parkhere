package com.drivewatch.core

import kotlin.math.abs
import kotlin.math.max

/** 한 주행 안에서 구간마다 비교하는 신호. margin은 평소 범위에 더하는 최소 여유다. */
enum class Signal(val key: String, val label: String, val unit: String, val margin: Double) {
    HARD_BRAKES("hardBrakes", "강한 제동", "회", 2.0),
    STEERING_CORRECTIONS("steeringCorrections", "조향 보정", "회", 2.0),
    MINUTES_SINCE_BREAK("minutesSinceBreak", "휴식 후 누적 주행", "분", 8.0),
}

enum class Gear { P, R, N, D }

/** 구간 하나의 요약. minute은 주행 시작 후 구간이 끝난 시각(분)이다. */
data class Frame(
    val minute: Double,
    val context: String,
    val gear: Gear? = null,
    val speedKph: Double? = null,
    /** 기어 신호가 없을 때, 실제로 움직였는지 측정한 값. */
    val driven: Boolean? = null,
    val values: Map<Signal, Double?> = emptyMap(),
)

/** 같은 운전자의 과거 구간 기록. */
data class HistoryFrame(val context: String, val values: Map<Signal, Double?>)

data class Baseline(val typical: Double, val upper: Double, val count: Int)

data class Reading(
    val signal: Signal,
    val value: Double?,
    val baseline: Baseline?,
    val above: Boolean,
    val streak: Int,
)

data class Detection(val minute: Double, val context: String, val evidence: List<Reading>)

enum class Observation(val key: String) {
    IDLE("idle"),
    INSUFFICIENT("insufficient"),
    TYPICAL("typical"),
    WATCHING("watching"),
    CHANGE("change"),
}

data class FrameResult(
    val readings: List<Reading>,
    val observation: Observation,
    val detection: Detection?,
    val parked: Boolean,
    val moving: Boolean,
)

/** 구간 판정. 같은 도로 조건의 개인 기준선과 비교하고, 같은 신호가 연속으로 넘을 때만 변화로 기록한다. */
object TripEngine {
    const val WINDOW_MINUTES = 2.0
    const val REQUIRED_WINDOWS = 2
    const val MIN_HISTORY = 5

    fun baselineFor(
        history: List<HistoryFrame>,
        context: String,
        margins: Map<Signal, Double> = emptyMap(),
    ): Map<Signal, Baseline?> {
        val matched = history.filter { it.context == context }
        return Signal.entries.associateWith { signal ->
            val values = matched.map { it.values[signal] }.filter { it.isUsable() }.map { it!! }
            if (values.size < MIN_HISTORY) {
                null
            } else {
                val typical = median(values)
                val deviation = median(values.map { abs(it - typical) })
                Baseline(typical, typical + max(margins[signal] ?: signal.margin, 3 * deviation), values.size)
            }
        }
    }

    fun createSession(
        history: List<HistoryFrame>,
        interval: Double = WINDOW_MINUTES,
        margins: Map<Signal, Double> = emptyMap(),
    ): TripSession = TripSession(history, interval, margins)
}

class TripSession internal constructor(
    private val history: List<HistoryFrame>,
    private val interval: Double,
    private val margins: Map<Signal, Double>,
) {
    private data class Last(val minute: Double, val context: String, val moving: Boolean)

    private val profiles = mutableMapOf<String, Map<Signal, Baseline?>>()
    private var last: Last? = null
    private var streaks = mutableMapOf<Signal, Int>()
    private var detection: Detection? = null

    /** 구간을 시간 순서대로 넣는다. 순서가 어긋나면 IllegalArgumentException. */
    fun ingest(frame: Frame): FrameResult {
        val previous = last
        require(frame.minute.isUsable() && (previous == null || frame.minute > previous.minute)) {
            "주행 기록은 시간 순서대로 입력해야 합니다."
        }
        val profile = profiles.getOrPut(frame.context) {
            TripEngine.baselineFor(history, frame.context, margins)
        }
        val speed = frame.speedKph
        val drivingInput = frame.gear == Gear.D || (frame.gear == null && frame.driven == true)
        val moving = drivingInput && speed.isUsable() && (frame.driven ?: (speed!! > 0))
        val parked = frame.gear == Gear.P && speed != null && speed == 0.0
        val contiguous = moving && previous != null && previous.moving &&
            previous.context == frame.context &&
            abs(frame.minute - previous.minute - interval) < 1e-9
        if (!contiguous) streaks = mutableMapOf()

        val readings = Signal.entries.map { signal ->
            val raw = frame.values[signal]
            val value = if (moving && raw.isUsable()) raw else null
            val baseline = profile[signal]
            val above = value != null && baseline != null && value > baseline.upper
            val streak = if (above) (streaks[signal] ?: 0) + 1 else 0
            streaks[signal] = streak
            Reading(signal, value, baseline, above, streak)
        }
        val confirmed = readings.filter { it.streak >= TripEngine.REQUIRED_WINDOWS }
        val compared = readings.filter { it.value != null && it.baseline != null }
        if (detection == null && confirmed.isNotEmpty()) {
            detection = Detection(frame.minute, frame.context, confirmed)
        }

        last = Last(frame.minute, frame.context, moving)
        var observation = Observation.IDLE
        if (moving) {
            observation = if (compared.isEmpty()) Observation.INSUFFICIENT else Observation.TYPICAL
            if (readings.any { it.above }) observation = Observation.WATCHING
            if (confirmed.isNotEmpty()) observation = Observation.CHANGE
        }
        return FrameResult(readings, observation, detection, parked, moving)
    }
}
