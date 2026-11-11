package com.parkhere.drivewatch

/** One finished drive. weekAgo 0 = this week. */
data class Trip(
    val weekAgo: Int,
    val km: Double,
    val familiar: Boolean,
    val night: Boolean,
    val reroutes: Int,
    val turnsGuided: Int,
    val signalsUsed: Int,
    val hardBrakes: Int,
    val place: String = "",
)

/** Change-detection metrics. All are "lower is better" internally; signal is shown as compliance %. */
enum class Metric(val label: String) {
    REROUTE("익숙한 길 재탐색"),
    SIGNAL("깜빡이 이행률"),
    BRAKE("급제동");

    /** Bad-rate for one week, or null when the sample is too small to judge. */
    fun rate(trips: List<Trip>): Double? = when (this) {
        REROUTE -> {
            val f = trips.filter { it.familiar }
            val km = f.sumOf { it.km }
            if (km < 5) null else f.sumOf { it.reroutes } * 10 / km
        }
        SIGNAL -> {
            val turns = trips.sumOf { it.turnsGuided }
            if (turns < 5) null else 100.0 * (turns - trips.sumOf { it.signalsUsed }) / turns
        }
        BRAKE -> {
            val km = trips.sumOf { it.km }
            if (km < 20) null else trips.sumOf { it.hardBrakes } * 100 / km
        }
    }

    /** Value as the driver sees it. */
    fun display(rate: Double): String = when (this) {
        REROUTE -> "%.1f회/10km".format(rate)
        SIGNAL -> "%.0f%%".format(100 - rate)
        BRAKE -> "%.1f회/100km".format(rate)
    }

    /** Smallest absolute rise that counts, so a near-zero baseline does not flag noise. */
    val floor: Double get() = when (this) { REROUTE -> 0.3; SIGNAL -> 5.0; BRAKE -> 0.5 }
}

enum class State(val label: String) { LEARNING("학습 중"), STABLE("안정"), WATCH("관찰"), TALK("대화 권장") }

data class MetricReport(
    val metric: Metric,
    val weekly: List<Double?>, // index 0 = this week, oldest last
    val baseline: Double?,
    val worseStreak: Int,
)

data class Report(val state: State, val metrics: List<MetricReport>, val nightDrivesThisWeek: Int) {
    val changed: List<MetricReport> get() = metrics.filter { it.worseStreak >= 2 }
}

/**
 * Baseline = average of the 8 weeks before the latest 4 (weeks 4..11).
 * A week is "worse" when its rate is >= 1.5x baseline and at least `floor` above it.
 * WATCH: one metric worse 2 weeks running. TALK: two such metrics, or one for 4 weeks.
 */
fun analyze(trips: List<Trip>, weeks: Int = 12): Report {
    val byWeek = (0 until weeks).map { w -> trips.filter { it.weekAgo == w } }
    val nightNow = byWeek[0].count { it.night }
    if (trips.maxOfOrNull { it.weekAgo } ?: 0 < weeks - 1) {
        return Report(State.LEARNING, Metric.entries.map { m -> MetricReport(m, byWeek.map(m::rate), null, 0) }, nightNow)
    }
    val reports = Metric.entries.map { m ->
        val weekly = byWeek.map(m::rate)
        val base = weekly.drop(4).filterNotNull().takeIf { it.size >= 4 }?.average()
        val streak = if (base == null) 0 else weekly.take(4).takeWhile { r ->
            r != null && r >= base * 1.5 && r - base >= m.floor
        }.size
        MetricReport(m, weekly, base, streak)
    }
    val worse = reports.filter { it.worseStreak >= 2 }
    val state = when {
        worse.size >= 2 || reports.any { it.worseStreak >= 4 } -> State.TALK
        worse.isNotEmpty() -> State.WATCH
        else -> State.STABLE
    }
    return Report(state, reports, nightNow)
}
