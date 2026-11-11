package com.drivewatch.vehicle

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.drivewatch.core.collect.BrakeState
import com.drivewatch.core.collect.GearState
import com.drivewatch.core.collect.SignalEvent
import com.drivewatch.core.collect.SignalSource
import com.drivewatch.core.collect.SpeedSample
import com.drivewatch.core.collect.SteeringSample

/**
 * 녹화한 신호를 녹화된 간격 그대로 흘려보낸다. 차량 신호와 같은 입구로 들어가므로 수집과 판정은 차이를 모른다.
 * 시연에서 Virtual Console을 쓸 수 없을 때의 예비 수단이다. 화면에는 "시연용"으로 표기한다.
 */
class ReplaySignalSource(private val events: List<SignalEvent>) : SignalSource {
    private val main = Handler(Looper.getMainLooper())

    override fun start(sink: (SignalEvent) -> Unit) {
        stop()
        val first = events.minOfOrNull { it.timeMs } ?: return
        val base = SystemClock.elapsedRealtime()
        for (event in events) {
            val at = base + (event.timeMs - first)
            main.postAtTime({ sink(event.at(at)) }, SystemClock.uptimeMillis() + (event.timeMs - first))
        }
    }

    override fun stop() = main.removeCallbacksAndMessages(null)

    private fun SignalEvent.at(timeMs: Long): SignalEvent = when (this) {
        is SpeedSample -> copy(timeMs = timeMs)
        is BrakeState -> copy(timeMs = timeMs)
        is SteeringSample -> copy(timeMs = timeMs)
        is GearState -> copy(timeMs = timeMs)
    }
}
