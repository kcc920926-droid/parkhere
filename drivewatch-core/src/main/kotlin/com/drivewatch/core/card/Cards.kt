package com.drivewatch.core.card

import com.drivewatch.core.Signal
import com.drivewatch.core.Week
import com.drivewatch.core.WeeklyMetric
import com.drivewatch.core.WeeklyStatus
import com.drivewatch.core.collect.TripReport
import java.time.LocalDate
import kotlin.math.roundToInt

/** 주차 후 카드에 쓸 문구. 화면은 이 문구를 그리기만 한다. */
data class ArrivalCard(
    val changed: Boolean,
    val title: String,
    /** 예: "강한 제동 · 2분 구간당 평소 3회 이하, 이번 최고 5회" */
    val evidence: String?,
    /** 예: "출발 후 6–8분 4회, 8–10분 5회" */
    val where: String?,
    /** 구간 상세에 쓸 값. 변화가 없으면 강한 제동을 보여 준다. */
    val signal: Signal,
    val upper: Double?,
    val windows: List<WindowBar>,
)

data class WindowBar(val label: String, val value: Double?, val above: Boolean)

/** 숫자 근거 한 줄. 가족에게 보낼 때도 같은 값을 쓴다. */
data class EvidenceLine(val label: String, val unit: String, val typical: Double, val value: Double, val weeks: Int) {
    /** 예: "강한 제동 · 100km당 6.3회 → 21.9회 · 4주 연속" */
    val text: String
        get() {
            val parts = unit.split("/")
            val per = parts.getOrNull(1)?.let { "${it}당 " } ?: ""
            return "$label · $per${number(typical)}${parts[0]} → ${number(value)}${parts[0]} · ${weeks}주 연속"
        }
}

/** 이번 주 카드에 쓸 문구. */
data class WeeklyCard(
    val week: LocalDate,
    val status: WeeklyStatus,
    val chip: String,
    val headline: String,
    val evidence: List<EvidenceLine>,
    /** 대화 권장일 때만 붙는 한 줄. */
    val ask: String?,
    /** 평소 범위였던 지표. */
    val goodPoints: List<String>,
    /** 지난 주들의 상태. 오래된 주부터. */
    val strip: List<WeeklyStatus>,
)

private fun number(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else value.toString()

object Cards {
    /** 구간 길이를 사람이 읽는 단위로. 시연 모드(1분 미만)는 초로 적는다. */
    private fun unitOf(windowMinutes: Double) = if (windowMinutes >= 1) "분" else "초"
    private fun lengthOf(windowMinutes: Double) =
        if (windowMinutes >= 1) number(windowMinutes) else number(windowMinutes * 60)

    private fun span(index: Int, windowMinutes: Double): String {
        val size = if (windowMinutes >= 1) windowMinutes else windowMinutes * 60
        return "${number(index * size)}–${number((index + 1) * size)}${unitOf(windowMinutes)}"
    }

    fun arrival(report: TripReport): ArrivalCard {
        val detection = report.detection
        val signal = detection?.evidence?.first()?.signal ?: Signal.HARD_BRAKES
        val upper = report.baselines[signal]?.upper
        val windows = report.frames.mapIndexed { index, frame ->
            val value = frame.values[signal]
            WindowBar(span(index, report.windowMinutes), value, value != null && upper != null && value > upper)
        }
        if (detection == null || upper == null) {
            val compared = report.results.any { result -> result.readings.any { it.value != null && it.baseline != null } }
            val title = if (compared) "평소와 비슷했어요" else "평소 주행을 알아가는 중이에요"
            return ArrivalCard(false, title, null, null, signal, upper, windows)
        }
        val worst = windows.mapNotNull { it.value }.max()
        val evidence = "${signal.label} · ${lengthOf(report.windowMinutes)}${unitOf(report.windowMinutes)} 구간당 " +
            "평소 ${number(upper)}${signal.unit} 이하, 이번 최고 ${number(worst)}${signal.unit}"
        val where = "출발 후 " + windows.filter { it.above }.joinToString(", ") { "${it.label} ${number(it.value!!)}${signal.unit}" }
        return ArrivalCard(true, "평소와 달라요", evidence, where, signal, upper, windows)
    }

    /** 판정된 주들 가운데 주행이 충분했던 마지막 주로 카드를 만든다. 그런 주가 없으면 null. */
    fun weekly(weeks: List<Week>): WeeklyCard? {
        val index = weeks.indexOfLast { it.sampled || it.status == WeeklyStatus.LEARNING }
        if (index < 0) return null
        val week = weeks[index]
        val evidence = week.evidence.map {
            EvidenceLine(it.metric.label, it.metric.unit, it.baseline!!.typical, it.value!!, it.streak)
        }
        val phrases = week.evidence.map {
            val more = if (it.metric.kind == WeeklyMetric.Kind.MAX) "길었어요" else "많았어요"
            Triple(it.metric.label, it.streak, more)
        }
        val headline = when (week.status) {
            WeeklyStatus.LEARNING -> "평소 운전을 알아가는 중이에요"
            WeeklyStatus.INSUFFICIENT -> "이번 주는 주행이 적어서 비교하지 않았어요"
            WeeklyStatus.STABLE -> "이번 주도 평소처럼 운전하셨어요"
            WeeklyStatus.OBSERVE, WeeklyStatus.TALK ->
                phrases.joinToString(", ") { (label, streak, _) -> "${label}이 ${streak}주째" } + " 평소보다 ${phrases.last().third}"
        }
        val chip = when (week.status) {
            WeeklyStatus.LEARNING -> "학습 중"
            WeeklyStatus.INSUFFICIENT -> "자료 부족"
            WeeklyStatus.STABLE -> "안정"
            WeeklyStatus.OBSERVE -> "관찰"
            WeeklyStatus.TALK -> "대화 권장"
        }
        val good = week.readings.filter { it.compared && !it.above }.map { "${it.metric.label}은 평소 범위였어요" }
        return WeeklyCard(
            week = week.week,
            status = week.status,
            chip = chip,
            headline = headline,
            evidence = evidence,
            ask = if (week.status == WeeklyStatus.TALK) "가족과 이야기해 보실래요?" else null,
            goodPoints = good,
            strip = weeks.take(index + 1).map { it.status },
        )
    }
}
