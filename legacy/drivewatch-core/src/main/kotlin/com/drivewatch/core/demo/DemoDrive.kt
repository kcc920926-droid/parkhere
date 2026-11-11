package com.drivewatch.core.demo

import com.drivewatch.core.Gear
import com.drivewatch.core.collect.BrakeState
import com.drivewatch.core.collect.GearState
import com.drivewatch.core.collect.SignalEvent
import com.drivewatch.core.collect.SpeedSample
import com.drivewatch.core.collect.SteeringSample
import kotlin.math.sin

/**
 * 시연용 녹화 신호. 도심 예시 기록([DemoReplays]의 '도심 기록')과 같은 주행을 신호 단위로 풀어 쓴 것이다.
 * 구간마다 강한 제동이 1·0·1·4·5·1회 나오고, 네 번째와 다섯 번째 구간에서 평소 범위를 연속으로 넘는다.
 * 실차 기록이 아니다.
 */
object DemoDrive {
    val hardBrakesPerWindow = listOf(1, 0, 1, 4, 5, 1)

    private const val CRUISE = 37 / 3.6
    private const val STEP_MS = 100L

    /** 0ms에 D단으로 출발해서, 구간이 모두 끝난 뒤 멈추고 P단에 넣는다. */
    fun events(windowMs: Long = 8_000, brakesPerWindow: List<Int> = hardBrakesPerWindow): List<SignalEvent> {
        val events = mutableListOf<SignalEvent>(GearState(0, Gear.D), BrakeState(0, false))
        val driveMs = windowMs * brakesPerWindow.size
        val brakeStarts = brakesPerWindow.flatMapIndexed { window, count ->
            val spacing = windowMs / (count + 1)
            (1..count).map { window * windowMs + it * spacing - 400 }
        }
        var speed = 0.0
        var pressed = false
        for (time in 0..driveMs + 3_000 step STEP_MS) {
            val braking = brakeStarts.any { time in it until it + 800 }
            val stopping = time > driveMs + 300
            val target = when {
                stopping -> 0.0
                braking -> CRUISE - 5.5
                else -> CRUISE
            }
            val rate = if (braking || stopping) 6.5 else 4.0
            val step = rate * STEP_MS / 1000.0
            speed = if (speed > target) maxOf(target, speed - step) else minOf(target, speed + step)
            val nowPressed = braking || stopping
            if (nowPressed != pressed) {
                pressed = nowPressed
                events += BrakeState(time, pressed)
            }
            events += SpeedSample(time, speed)
            // 평소의 잔잔한 조향에, 구간마다 두 번쯤 되돌리는 움직임을 얹는다.
            val angle = 2.0 * sin(time / 900.0) + 7.0 * sin(time * 2 * Math.PI / (windowMs / 1.5))
            events += SteeringSample(time, angle)
        }
        val end = driveMs + 3_100
        events += SpeedSample(end, 0.0)
        events += GearState(end, Gear.P)
        return events
    }
}
