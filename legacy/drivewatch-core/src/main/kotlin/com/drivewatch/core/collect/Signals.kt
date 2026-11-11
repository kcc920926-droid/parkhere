package com.drivewatch.core.collect

import com.drivewatch.core.Gear

/** 차량에서 들어오는 신호 하나. timeMs는 단조 증가하는 시계의 밀리초다(벽시계가 아니다). */
sealed interface SignalEvent {
    val timeMs: Long
}

data class SpeedSample(override val timeMs: Long, val metersPerSecond: Double) : SignalEvent

/** Pleos Vehicle SDK는 브레이크가 눌렸는지만 알려 준다. 압력이나 감속도는 오지 않는다. */
data class BrakeState(override val timeMs: Long, val pressed: Boolean) : SignalEvent

data class SteeringSample(override val timeMs: Long, val angleDeg: Double) : SignalEvent

data class GearState(override val timeMs: Long, val gear: Gear?) : SignalEvent

/** 신호가 들어오는 입구. 차량 신호와 녹화한 신호를 같은 자리에 끼울 수 있게 한다. */
interface SignalSource {
    fun start(sink: (SignalEvent) -> Unit)
    fun stop()
}

/**
 * 구간을 나누고 횟수를 세는 기준. 값은 웹 MVP의 시연 기준을 따랐고, 실차에서 쓸 값은 따로 검증해야 한다.
 */
data class CollectorConfig(
    /** 구간 길이. 제품은 2분, 시연은 [demo]의 8초. */
    val windowMs: Long = 120_000,
    /** 이 감속도(m/s²) 이상이면 강한 제동 한 번으로 센다. */
    val hardBrakeDecel: Double = 4.0,
    /** 감속도는 이만큼 이전의 차속과 비교해 구한다. 표본 하나의 튐에 흔들리지 않게 하려는 것이다. */
    val decelSpanMs: Long = 500,
    /** 비교할 이전 차속이 이보다 오래됐으면 감속도를 구하지 않는다. */
    val decelMaxSpanMs: Long = 1_000,
    /** 브레이크 신호가 없을 때, 감속도가 이 값 아래로 내려가야 다음 제동을 센다. */
    val rearmDecel: Double = 3.0,
    /** 조향각이 방향을 바꿔 이만큼(도) 되돌아오면 조향 보정 한 번으로 센다. */
    val steeringGapDeg: Double = 5.0,
    /** 이 속도(m/s) 아래에서는 조향 보정을 세지 않는다. 주차 조작을 빼려는 것이다. */
    val steeringMinSpeed: Double = 10 / 3.6,
    /** P단으로 이만큼 서 있으면 쉰 것으로 보고 누적 주행 시간을 0으로 돌린다. */
    val breakMs: Long = 5 * 60_000,
) {
    val windowMinutes: Double get() = windowMs / 60_000.0

    companion object {
        /** 시연 모드: 구간만 8초로 줄인다. 웹 MVP의 가상 코스와 같다. */
        fun demo(windowMs: Long = 8_000) = CollectorConfig(windowMs = windowMs, breakMs = windowMs * 5 / 2)
    }
}
