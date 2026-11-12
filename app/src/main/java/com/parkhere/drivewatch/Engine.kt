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
    /** 시연용 synthetic history: never uploaded, replaced when another history scenario is chosen. */
    val demo: Boolean = false,
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
) {
    /** Index in [weekly] of the newest week with enough driving to judge, or -1. The streak ends there. */
    val judged: Int get() = weekly.indexOfFirst { it != null }

    /** Rate of that week. Null only when no recent week could be judged. */
    val latest: Double? get() = weekly.getOrNull(judged)

    /** One line of evidence for a changed metric, as the cards show it. */
    fun evidenceLine(): String {
        val b = baseline ?: return metric.label
        val v = latest ?: return metric.label
        return "${metric.label} · 평소 ${metric.display(b)} → ${if (judged == 0) "이번 주" else "최근"} ${metric.display(v)} · ${worseStreak}주 연속"
    }
}

data class Report(val state: State, val metrics: List<MetricReport>, val nightDrivesThisWeek: Int) {
    val changed: List<MetricReport> get() = metrics.filter { it.worseStreak >= 2 }
}

const val BASELINE_WEEKS = 8
const val MIN_BASELINE_WEEKS = 4

/**
 * Weeks are judged oldest first. Baseline = average of the last 8 weeks that were not part of a change
 * (at least 4 needed). A week is "worse" when its rate is >= 1.5x baseline and at least `floor` above it.
 * - Two or more worse weeks in a row are a change and stay out of later baselines, so a change that
 *   goes on for months does not become the new normal. A single worse week is noise and stays in.
 * - A week without enough driving breaks the streak, except at the newest end: while this week has too
 *   little driving to judge, the last judged week's result stands.
 * WATCH: one metric worse 2 weeks running. TALK: two such metrics, or one for 4 weeks.
 */
fun analyze(trips: List<Trip>, weeks: Int = 12): Report {
    val oldest = trips.maxOfOrNull { it.weekAgo } ?: 0
    val byWeek = (0..maxOf(oldest, weeks - 1)).map { w -> trips.filter { it.weekAgo == w } }
    val nightNow = byWeek[0].count { it.night }
    if (oldest < weeks - 1) {
        return Report(State.LEARNING, Metric.entries.map { m -> MetricReport(m, byWeek.take(weeks).map(m::rate), null, 0) }, nightNow)
    }
    val reports = Metric.entries.map { m -> judge(m, byWeek.map(m::rate), weeks) }
    val worse = reports.filter { it.worseStreak >= 2 }
    val state = when {
        worse.size >= 2 || reports.any { it.worseStreak >= 4 } -> State.TALK
        worse.isNotEmpty() -> State.WATCH
        else -> State.STABLE
    }
    return Report(state, reports, nightNow)
}

/** rates: index 0 = this week, oldest last (may be longer than the `weeks` shown). */
private fun judge(m: Metric, rates: List<Double?>, weeks: Int): MetricReport {
    val shown = rates.take(weeks)
    val newest = shown.indexOfFirst { it != null }
    if (newest < 0) return MetricReport(m, shown, null, 0)
    val normal = mutableListOf<Double>()  // weeks that count as "usual", oldest first
    val pending = mutableListOf<Double>() // the current run of worse weeks
    var base: Double? = null
    for (w in rates.lastIndex downTo newest) {
        base = normal.takeLast(BASELINE_WEEKS).takeIf { it.size >= MIN_BASELINE_WEEKS }?.average()
        val r = rates[w]
        if (r != null && base != null && r >= base * 1.5 && r - base >= m.floor) {
            pending += r
            continue
        }
        if (pending.size < 2) normal += pending
        pending.clear()
        if (r != null) normal += r
    }
    return MetricReport(m, shown, base, pending.size)
}
