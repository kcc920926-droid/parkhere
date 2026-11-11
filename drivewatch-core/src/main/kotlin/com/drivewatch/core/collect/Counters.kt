package com.drivewatch.core.collect

import kotlin.math.abs
import kotlin.math.sign

/**
 * 강한 제동을 센다. 브레이크는 눌림 여부만 오므로, 브레이크가 눌린 동안의 차속 감소율로 판단한다.
 * 한 번 센 뒤에는 브레이크를 뗐다가 다시 밟아야 다음 제동을 센다.
 * 브레이크 신호가 한 번도 오지 않았으면 감속도만으로 세고, 감속도가 충분히 줄어야 다음을 센다.
 */
internal class HardBrakeCounter(private val config: CollectorConfig) {
    private val recent = ArrayDeque<SpeedSample>()
    private var armed = true

    var brakePressed: Boolean? = null
        private set
    var lastDeceleration: Double? = null
        private set

    fun onBrake(pressed: Boolean) {
        brakePressed = pressed
        if (!pressed) armed = true
    }

    /** 이 표본으로 강한 제동 한 번이 성립하면 true. */
    fun onSpeed(sample: SpeedSample): Boolean {
        if (recent.isNotEmpty() && sample.timeMs <= recent.last().timeMs) return false
        while (recent.isNotEmpty() && recent.first().timeMs < sample.timeMs - config.decelMaxSpanMs) recent.removeFirst()
        val reference = recent.lastOrNull { it.timeMs <= sample.timeMs - config.decelSpanMs }
        recent.addLast(sample)
        val deceleration = reference?.let {
            (it.metersPerSecond - sample.metersPerSecond) / ((sample.timeMs - it.timeMs) / 1000.0)
        }
        lastDeceleration = deceleration
        val pressed = brakePressed
        if (deceleration == null) {
            if (pressed == null) armed = true
            return false
        }
        if (pressed == false) return false
        val hit = armed && deceleration >= config.hardBrakeDecel
        if (hit) armed = false
        if (pressed == null && deceleration < config.rearmDecel) armed = true
        return hit
    }
}

/**
 * 조향 보정을 센다. 조향각이 한쪽으로 가다가 방향을 바꿔 [CollectorConfig.steeringGapDeg] 이상 되돌아오면 한 번이다.
 * 운전 연구에서 쓰는 조향 반전 횟수와 같은 방식이다. 회전 한 번도 되돌아올 때 한 번으로 세지만,
 * 비교 기준이 본인의 같은 길 기록이므로 길 모양에서 오는 횟수는 기준선에 함께 들어간다.
 */
internal class SteeringReversalCounter(private val gapDeg: Double) {
    private var extreme: Double? = null
    private var direction = 0

    /** 이 각도로 방향 전환 한 번이 성립하면 true. */
    fun onAngle(angleDeg: Double): Boolean {
        val last = extreme
        if (last == null) {
            extreme = angleDeg
            return false
        }
        val moved = angleDeg - last
        if (direction == 0) {
            if (abs(moved) >= gapDeg) {
                direction = sign(moved).toInt()
                extreme = angleDeg
            }
            return false
        }
        if (moved * direction > 0) {
            extreme = angleDeg
            return false
        }
        if (abs(moved) < gapDeg) return false
        direction = -direction
        extreme = angleDeg
        return true
    }
}
