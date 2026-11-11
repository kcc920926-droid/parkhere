package com.drivewatch.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max

/** 주 단위로 비교하는 지표. RATE는 100km당 횟수, MAX는 그 주의 최댓값이다. */
enum class WeeklyMetric(
    val key: String,
    val label: String,
    val unit: String,
    val source: Signal,
    val kind: Kind,
    val margin: Double,
) {
    HARD_BRAKES_PER_100KM("hardBrakesPer100km", "강한 제동", "회/100km", Signal.HARD_BRAKES, Kind.RATE, 3.0),
    STEERING_PER_100KM("steeringPer100km", "조향 보정", "회/100km", Signal.STEERING_CORRECTIONS, Kind.RATE, 6.0),
    LONGEST_DRIVE_MINUTES("longestDriveMinutes", "쉬지 않고 운전한 시간", "분", Signal.MINUTES_SINCE_BREAK, Kind.MAX, 15.0);

    enum class Kind { RATE, MAX }
}

/** 주행 한 번의 요약. */
data class Trip(
    val date: LocalDate,
    val context: String?,
    val km: Double,
    val values: Map<Signal, Double?>,
)

data class WeeklyBaseline(val typical: Double, val upper: Double, val weeks: Int)

data class WeeklyReading(
    val metric: WeeklyMetric,
    val value: Double?,
    val baseline: WeeklyBaseline?,
    val compared: Boolean,
    val above: Boolean,
    val streak: Int,
)

enum class WeeklyStatus(val key: String) {
    LEARNING("learning"),
    INSUFFICIENT("insufficient"),
    STABLE("stable"),
    OBSERVE("observe"),
    TALK("talk"),
}

data class Week(
    /** 그 주의 월요일. */
    val week: LocalDate,
    val context: String?,
    val trips: Int,
    val km: Double,
    val tripsSoFar: Int,
    /** 주행이 충분해서 다음 기준선에 들어갈 수 있는 주인지. */
    val sampled: Boolean,
    val values: Map<WeeklyMetric, Double?>,
    val readings: List<WeeklyReading>,
    val status: WeeklyStatus,
    val evidence: List<WeeklyReading>,
)

/** 주간 판정. 한 운전자의 이번 주를 그 운전자의 지난 주들과 비교한다. */
object WeeklyEngine {
    const val LEARNING_DAYS = 14L
    const val LEARNING_TRIPS = 10
    const val BASELINE_WEEKS = 8
    const val MIN_BASELINE_WEEKS = 2
    const val MIN_TRIPS_PER_WEEK = 3
    const val OBSERVE_WEEKS = 2
    const val TALK_WEEKS = 4

    /** 날짜가 속한 주의 월요일. */
    fun weekOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekOf(date: String): LocalDate = try {
        weekOf(LocalDate.parse(date))
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("날짜 형식이 올바르지 않습니다: $date", error)
    }

    /** 구간 판정이 읽는 구간 기록에서 주행 한 번의 요약을 만든다. */
    fun tripFromFrames(
        frames: List<Frame>,
        date: LocalDate,
        context: String? = null,
        interval: Double = TripEngine.WINDOW_MINUTES,
    ): Trip {
        val moving = frames.filter { it.gear == Gear.D && it.speedKph.isUsable() && it.speedKph!! > 0 }
        fun usable(signal: Signal) = moving.map { it.values[signal] }.filter { it.isUsable() }.map { it!! }
        fun sum(signal: Signal) = usable(signal).takeIf { it.isNotEmpty() }?.sum()
        return Trip(
            date = date,
            context = context ?: moving.firstOrNull()?.context,
            km = round1(moving.sumOf { it.speedKph!! * interval / 60 }),
            values = mapOf(
                Signal.HARD_BRAKES to sum(Signal.HARD_BRAKES),
                Signal.STEERING_CORRECTIONS to sum(Signal.STEERING_CORRECTIONS),
                Signal.MINUTES_SINCE_BREAK to usable(Signal.MINUTES_SINCE_BREAK).maxOrNull(),
            ),
        )
    }

    private fun weekValues(trips: List<Trip>): Map<WeeklyMetric, Double?> =
        WeeklyMetric.entries.associateWith { metric ->
            val used = trips.filter {
                it.values[metric.source].isUsable() && (metric.kind != WeeklyMetric.Kind.RATE || it.km > 0)
            }
            when {
                used.isEmpty() -> null
                metric.kind == WeeklyMetric.Kind.MAX -> used.maxOf { it.values[metric.source]!! }
                else -> round1(used.sumOf { it.values[metric.source]!! } / used.sumOf { it.km } * 100)
            }
        }

    /** 첫 주행이 있는 주부터 마지막 주행이 있는 주까지, 도로 조건 하나를 기준으로 주마다 판정한다. */
    fun assess(trips: List<Trip>, context: String? = null): List<Week> {
        val sorted = trips.sortedBy { it.date }
        if (sorted.isEmpty()) return emptyList()
        val learnUntil = sorted.first().date.plusDays(LEARNING_DAYS)
        val first = weekOf(sorted.first().date)
        val last = weekOf(sorted.last().date)
        val weeks = mutableListOf<Week>()
        var streaks = emptyMap<WeeklyMetric, Int>()
        var tripsSoFar = 0

        var start = first
        while (!start.isAfter(last)) {
            val all = sorted.filter { weekOf(it.date) == start }
            tripsSoFar += all.size
            val own = all.filter { context == null || it.context == context }
            val values = weekValues(own)
            val learningWeek = !start.plusDays(7).isAfter(learnUntil) || tripsSoFar < LEARNING_TRIPS
            val enoughTrips = own.size >= MIN_TRIPS_PER_WEEK

            val readings = WeeklyMetric.entries.map { metric ->
                val past = weeks.takeLast(BASELINE_WEEKS)
                    .filter { entry -> entry.sampled && !entry.readings.first { it.metric == metric }.above }
                    .map { it.values[metric] }
                    .filter { it.isUsable() }
                    .map { it!! }
                val baseline = if (past.size >= MIN_BASELINE_WEEKS) {
                    val typical = median(past)
                    val deviation = median(past.map { abs(it - typical) })
                    WeeklyBaseline(round1(typical), round1(typical + max(metric.margin, 3 * deviation)), past.size)
                } else {
                    null
                }
                val value = values[metric]
                val compared = !learningWeek && enoughTrips && value != null && baseline != null
                val above = compared && value!! > baseline!!.upper
                val streak = if (above) (streaks[metric] ?: 0) + 1 else 0
                WeeklyReading(metric, value, baseline, compared, above, streak)
            }
            streaks = readings.associate { it.metric to it.streak }

            val worsening = readings.filter { it.streak >= OBSERVE_WEEKS }
            val status = when {
                learningWeek -> WeeklyStatus.LEARNING
                !enoughTrips || readings.none { it.compared } -> WeeklyStatus.INSUFFICIENT
                worsening.size >= 2 || readings.any { it.streak >= TALK_WEEKS } -> WeeklyStatus.TALK
                worsening.size == 1 -> WeeklyStatus.OBSERVE
                else -> WeeklyStatus.STABLE
            }

            weeks += Week(
                week = start,
                context = context,
                trips = own.size,
                km = round1(own.sumOf { it.km }),
                tripsSoFar = tripsSoFar,
                sampled = enoughTrips,
                values = values,
                readings = readings,
                status = status,
                evidence = if (status == WeeklyStatus.OBSERVE || status == WeeklyStatus.TALK) worsening else emptyList(),
            )
            start = start.plusDays(7)
        }
        return weeks
    }
}
