package com.drivewatch.core.demo

import com.drivewatch.core.Signal
import com.drivewatch.core.Trip
import com.drivewatch.core.WeeklyEngine
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** 시연용 합성 12주 도심 기록: 한 운전자, 주 4회, 회당 8km. 실차 기록이 아니다. */
object DemoWeeklyHistory {
    data class Scene(
        val id: String,
        val title: String,
        val hardBrakes: List<Int>,
        val steeringCorrections: List<Int>,
    )

    /** 웹 MVP와 같은 고정 시작일. 대조 테스트가 이 날짜를 쓴다. */
    val fixedFirstMonday: LocalDate = LocalDate.of(2026, 8, 17)
    private val tripDays = listOf(0, 2, 4, 5)
    private const val KM = 8.0
    private val usualBrakes = listOf(1, 2, 2, 1, 2, 2, 1, 2)
    private val usualSteering = listOf(12, 11, 13, 12, 12, 13, 11, 12)

    val scenes = listOf(
        Scene("steady", "한 주만 달랐던 기록", usualBrakes + listOf(6, 2, 1, 2), usualSteering + listOf(12, 13, 12, 11)),
        Scene("changing", "변화가 이어진 기록", usualBrakes + listOf(6, 5, 6, 7), usualSteering + listOf(12, 13, 18, 19)),
    )

    /** 주간 합계를 주행별 상한을 넘지 않게 앞에서부터 나눈다. */
    private fun spread(total: Int, cap: Int) = tripDays.indices.map { min(cap, max(0, total - it * cap)) }

    private fun even(total: Int): List<Int> {
        val base = total / tripDays.size
        return tripDays.indices.map { base + if (it < total % tripDays.size) 1 else 0 }
    }

    /**
     * 오늘을 기준으로 한 시작 월요일. 앞에서부터 [completedWeeks]주가 오늘까지 모두 끝나 있도록 잡는다.
     * 12를 주면 12주 전체가 과거가 되고, 8을 주면 평소 8주만 과거가 되어 나머지 4주는 시계를 넘겨야 보인다.
     */
    fun firstMondayFor(today: LocalDate, completedWeeks: Int = 12): LocalDate =
        WeeklyEngine.weekOf(today.minusDays(tripDays.last().toLong())).minusWeeks((completedWeeks - 1).toLong())

    fun tripsFor(scene: Scene, firstMonday: LocalDate = fixedFirstMonday): List<Trip> = scene.hardBrakes.flatMapIndexed { week, brakes ->
        val brakesByTrip = spread(brakes, 2)
        val steeringByTrip = even(scene.steeringCorrections[week])
        tripDays.mapIndexed { index, offset ->
            Trip(
                date = firstMonday.plusDays((week * 7 + offset).toLong()),
                context = "city",
                km = KM,
                values = mapOf(
                    Signal.HARD_BRAKES to brakesByTrip[index].toDouble(),
                    Signal.STEERING_CORRECTIONS to steeringByTrip[index].toDouble(),
                    Signal.MINUTES_SINCE_BREAK to (20 + ((week + index) % 4) * 2).toDouble(),
                ),
            )
        }
    }
}
