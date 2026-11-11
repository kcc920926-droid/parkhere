package com.drivewatch.core.demo

import com.drivewatch.core.Frame
import com.drivewatch.core.Gear
import com.drivewatch.core.HistoryFrame
import com.drivewatch.core.Signal

/** 시연용 합성 기록. 값 순서는 [강한 제동, 조향 보정, 휴식 후 누적 주행(분)]이다. 실차 기록이 아니다. */
object DemoReplays {
    enum class Variant { REGULAR, CHANGED }

    data class Scene(
        val id: String,
        val title: String,
        val situation: String,
        val context: String,
        val start: Int,
        val regular: List<List<Int>>,
        val changed: List<List<Int>>,
    )

    val contexts = mapOf("city" to "도심 생활도로", "highway" to "고속도로")

    private fun valuesFor(row: List<Int>): Map<Signal, Double?> = mapOf(
        Signal.HARD_BRAKES to row[0].toDouble(),
        Signal.STEERING_CORRECTIONS to row[1].toDouble(),
        Signal.MINUTES_SINCE_BREAK to row[2].toDouble(),
    )

    val history: List<HistoryFrame> =
        listOf(listOf(0, 2, 11), listOf(1, 2, 12), listOf(1, 3, 13), listOf(2, 3, 14), listOf(1, 4, 15))
            .map { HistoryFrame("city", valuesFor(it)) } +
            listOf(listOf(0, 1, 68), listOf(0, 2, 70), listOf(1, 2, 72), listOf(1, 3, 74), listOf(2, 3, 76))
                .map { HistoryFrame("highway", valuesFor(it)) }

    val scenes = listOf(
        Scene(
            id = "city", title = "도심 기록", situation = "제동과 조향 비교", context = "city", start = 0,
            regular = listOf(listOf(1, 2, 2), listOf(0, 3, 4), listOf(4, 3, 6), listOf(1, 2, 8), listOf(1, 3, 10), listOf(0, 2, 12)),
            changed = listOf(listOf(1, 2, 2), listOf(0, 3, 4), listOf(1, 3, 6), listOf(4, 7, 8), listOf(5, 8, 10), listOf(1, 3, 12)),
        ),
        Scene(
            id = "highway", title = "고속도로 기록", situation = "누적 주행 시간 비교", context = "highway", start = 72,
            regular = listOf(listOf(1, 2, 64), listOf(0, 2, 66), listOf(1, 3, 68), listOf(0, 2, 70), listOf(1, 2, 72), listOf(0, 2, 74)),
            changed = listOf(listOf(1, 2, 74), listOf(0, 2, 76), listOf(1, 3, 78), listOf(0, 2, 80), listOf(1, 2, 82), listOf(0, 2, 84)),
        ),
    )

    /** 2분 구간 6개와, 마지막에 주차(P단, 0km/h) 구간 하나. */
    fun framesFor(scene: Scene, variant: Variant): List<Frame> {
        val rows = if (variant == Variant.REGULAR) scene.regular else scene.changed
        val frames = rows.mapIndexed { index, row ->
            Frame(
                minute = (scene.start + (index + 1) * 2).toDouble(),
                context = scene.context,
                gear = Gear.D,
                speedKph = if (scene.context == "highway") 86.0 else 37.0,
                values = valuesFor(row),
            )
        }
        return frames + Frame(minute = frames.last().minute + 1, context = scene.context, gear = Gear.P, speedKph = 0.0)
    }
}
