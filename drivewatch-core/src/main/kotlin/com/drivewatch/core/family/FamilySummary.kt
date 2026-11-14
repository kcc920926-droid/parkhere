package com.drivewatch.core.family

import com.drivewatch.core.WeeklyStatus
import com.drivewatch.core.card.EvidenceLine
import com.drivewatch.core.card.WeeklyCard
import java.time.LocalDate

/**
 * 가족에게 보내는 주간 요약. 서버의 `POST /api/car/summaries` 본문과 같은 모양이다.
 * 위치와 경로는 담지 않는다. 숫자 근거는 운전자가 범위를 '근거까지'로 골랐을 때만 서버에 남는다.
 */
data class FamilySummary(
    /** 그 주의 월요일. */
    val week: LocalDate,
    val status: WeeklyStatus,
    /** 한 줄 요약. 달라진 점이 없을 때 폰에 보인다. */
    val headline: String = "",
    /** 달라진 점을 말로 적은 것. 범위와 무관하게 전달된다. */
    val changes: List<String> = emptyList(),
    val evidence: List<EvidenceLine> = emptyList(),
    val goodPoints: List<String> = emptyList(),
    /** 약속 이행. 예: "해 지면 운전 안 하기 · 이번 주 지킴" */
    val promise: String = "",
    /** 피한 구간. 지명은 운전자가 붙인 이름만 쓴다. */
    val avoided: String = "",
) {
    fun toJson(): String = Json.write(
        linkedMapOf(
            "week" to week.toString(),
            "status" to status.key,
            "headline" to headline,
            "goodPoints" to goodPoints,
            "promise" to promise,
            "avoided" to avoided,
            "changes" to changes,
            "evidence" to evidence.map {
                linkedMapOf("label" to it.label, "unit" to it.unit, "typical" to it.typical, "value" to it.value, "weeks" to it.weeks)
            },
        ),
    )

    companion object {
        private const val MAX_ITEMS = 3

        /** 차 화면의 주간 카드를 그대로 가족에게 보낼 요약으로 바꾼다. 차에서 본 문구와 가족이 받는 문구가 같다. */
        fun from(card: WeeklyCard, promise: String = "", avoided: String = ""): FamilySummary {
            val changed = card.status == WeeklyStatus.OBSERVE || card.status == WeeklyStatus.TALK
            return FamilySummary(
                week = card.week,
                status = card.status,
                headline = if (changed) "" else card.headline,
                changes = if (changed) listOf(card.headline) else emptyList(),
                evidence = card.evidence.take(MAX_ITEMS),
                goodPoints = card.goodPoints.take(MAX_ITEMS),
                promise = promise,
                avoided = avoided,
            )
        }
    }
}
