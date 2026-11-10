package com.drivewatch.core.collect

import com.drivewatch.core.Frame
import com.drivewatch.core.Gear
import com.drivewatch.core.Signal
import kotlin.math.max

sealed interface CollectorOutput {
    data class TripStarted(val timeMs: Long) : CollectorOutput

    /** 구간 하나가 끝났다. */
    data class WindowClosed(val frame: Frame) : CollectorOutput

    /** P단으로 멈췄다. frame은 주차를 알리는 마지막 구간이고 신호 값이 없다. 끝나지 않은 구간은 버린다. */
    data class TripEnded(val frame: Frame) : CollectorOutput
}

/** 지금 진행 중인 구간의 값. 화면에 실시간으로 보여 줄 때 쓴다. */
data class LiveWindow(
    val tripActive: Boolean,
    val windowIndex: Int,
    val hardBrakes: Int,
    val steeringCorrections: Int,
    val speedKph: Double?,
    val deceleration: Double?,
    val brakePressed: Boolean?,
    val gear: Gear?,
)

/**
 * 신호를 받아 구간마다 [Frame] 하나로 묶는다. 주행은 D단에 들어갈 때 시작하고, P단으로 멈추면 끝난다.
 * 기어 신호가 한 번도 오지 않으면 차가 움직일 때 시작한다.
 */
class WindowCollector(
    private val config: CollectorConfig = CollectorConfig(),
    private val context: String = "city",
) {
    private val brakes = HardBrakeCounter(config)
    private var reversals = SteeringReversalCounter(config.steeringGapDeg)

    private var gear: Gear? = null
    private var gearSeen = false
    private var speed: Double? = null
    private var clockMs: Long? = null

    private var tripStartMs: Long? = null
    private var windowIndex = 0
    private var lastFrameMinute = 0.0

    private var hardBrakes = 0
    private var steering = 0
    private var speedSamples = 0
    private var decelSeen = false
    private var steeringSeen = false
    private var distanceM = 0.0
    private var maxSpeed = 0.0

    private var movingMsSinceBreak = 0.0
    private var lastTripEndMs: Long? = null

    val live: LiveWindow
        get() = LiveWindow(
            tripActive = tripStartMs != null, windowIndex = windowIndex, hardBrakes = hardBrakes,
            steeringCorrections = steering, speedKph = speed?.times(3.6), deceleration = brakes.lastDeceleration,
            brakePressed = brakes.brakePressed, gear = gear,
        )

    /** 신호 하나를 넣는다. 이 신호로 끝난 구간과 주행의 시작·끝을 돌려준다. */
    fun accept(event: SignalEvent): List<CollectorOutput> {
        val out = mutableListOf<CollectorOutput>()
        advanceTo(event.timeMs, out)
        when (event) {
            is GearState -> {
                gear = event.gear
                gearSeen = true
            }
            is BrakeState -> brakes.onBrake(event.pressed)
            is SpeedSample -> {
                val hit = brakes.onSpeed(event)
                speed = event.metersPerSecond
                if (tripStartMs != null) {
                    speedSamples++
                    maxSpeed = max(maxSpeed, event.metersPerSecond)
                    if (brakes.lastDeceleration != null) decelSeen = true
                    if (hit) hardBrakes++
                }
            }
            is SteeringSample -> {
                val turned = reversals.onAngle(event.angleDeg)
                if (tripStartMs != null) {
                    steeringSeen = true
                    if (turned && (speed ?: 0.0) >= config.steeringMinSpeed) steering++
                }
            }
        }
        val stopped = (speed ?: 0.0) == 0.0
        if (tripStartMs == null) {
            if (gear == Gear.D || (!gearSeen && !stopped)) startTrip(event.timeMs, out)
        } else if (gear == Gear.P && stopped) {
            endTrip(event.timeMs, out)
        }
        return out
    }

    /** 신호가 없어도 시간이 흘렀음을 알린다. 구간 경계를 넘겼으면 그 구간을 닫는다. */
    fun tick(timeMs: Long): List<CollectorOutput> = mutableListOf<CollectorOutput>().also { advanceTo(timeMs, it) }

    private fun startTrip(timeMs: Long, out: MutableList<CollectorOutput>) {
        val rested = lastTripEndMs?.let { timeMs - it >= config.breakMs } ?: true
        if (rested) movingMsSinceBreak = 0.0
        tripStartMs = timeMs
        windowIndex = 0
        lastFrameMinute = 0.0
        resetWindow()
        out += CollectorOutput.TripStarted(timeMs)
    }

    private fun endTrip(timeMs: Long, out: MutableList<CollectorOutput>) {
        val start = tripStartMs ?: return
        val minute = max((timeMs - start) / 60_000.0, lastFrameMinute + 1e-6)
        out += CollectorOutput.TripEnded(Frame(minute = minute, context = context, gear = Gear.P, speedKph = 0.0))
        tripStartMs = null
        lastTripEndMs = timeMs
        resetWindow()
    }

    private fun resetWindow() {
        hardBrakes = 0
        steering = 0
        speedSamples = 0
        decelSeen = false
        steeringSeen = false
        distanceM = 0.0
        maxSpeed = 0.0
    }

    /** 시계를 timeMs까지 옮기면서 그사이의 거리와 주행 시간을 쌓고, 넘긴 구간 경계마다 구간을 닫는다. */
    private fun advanceTo(timeMs: Long, out: MutableList<CollectorOutput>) {
        var from = clockMs ?: timeMs
        if (timeMs < from) return
        clockMs = timeMs
        val start = tripStartMs ?: return
        while (true) {
            val boundary = start + (windowIndex + 1) * config.windowMs
            val until = minOf(timeMs, boundary)
            accumulate(from, until)
            if (timeMs < boundary) return
            closeWindow(out)
            from = boundary
        }
    }

    private fun accumulate(fromMs: Long, toMs: Long) {
        val current = speed ?: return
        val seconds = (toMs - fromMs) / 1000.0
        if (seconds <= 0) return
        distanceM += current * seconds
        if (current > 0) movingMsSinceBreak += toMs - fromMs
    }

    private fun closeWindow(out: MutableList<CollectorOutput>) {
        windowIndex++
        val minute = windowIndex * config.windowMinutes
        val known = speedSamples > 0
        val frame = Frame(
            minute = minute,
            context = context,
            gear = gear,
            speedKph = if (known) distanceM / (config.windowMs / 1000.0) * 3.6 else null,
            driven = if (known) maxSpeed > 0 else null,
            values = mapOf(
                Signal.HARD_BRAKES to if (decelSeen) hardBrakes.toDouble() else null,
                Signal.STEERING_CORRECTIONS to if (steeringSeen) steering.toDouble() else null,
                Signal.MINUTES_SINCE_BREAK to if (known) movingMsSinceBreak / 60_000.0 else null,
            ),
        )
        lastFrameMinute = minute
        resetWindow()
        maxSpeed = speed ?: 0.0
        out += CollectorOutput.WindowClosed(frame)
    }
}
