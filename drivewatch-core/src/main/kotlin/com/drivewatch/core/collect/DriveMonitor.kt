package com.drivewatch.core.collect

import com.drivewatch.core.Baseline
import com.drivewatch.core.Detection
import com.drivewatch.core.Frame
import com.drivewatch.core.FrameResult
import com.drivewatch.core.HistoryFrame
import com.drivewatch.core.Signal
import com.drivewatch.core.TripEngine
import com.drivewatch.core.TripSession

/** 주행 한 번이 끝났을 때의 결과. 도착 카드와 구간 상세가 이것을 그린다. */
data class TripReport(
    val context: String,
    val windowMinutes: Double,
    /** 끝난 구간들. 마지막 주차 구간은 들어 있지 않다. */
    val frames: List<Frame>,
    val results: List<FrameResult>,
    /** 처음 감지한 변화와 근거. 없으면 null. */
    val detection: Detection?,
    val baselines: Map<Signal, Baseline?>,
)

sealed interface MonitorUpdate {
    data class TripStarted(val timeMs: Long) : MonitorUpdate
    data class WindowJudged(val frame: Frame, val result: FrameResult) : MonitorUpdate
    data class Arrived(val report: TripReport) : MonitorUpdate
}

/**
 * 신호 → 구간 → 판정을 잇는다. 신호가 어디서 왔는지(차량인지 녹화인지)는 모른다.
 * 주행마다 판정 세션을 새로 만들고, 주차하면 그 주행의 결과를 내놓는다.
 */
class DriveMonitor(
    private val history: List<HistoryFrame>,
    private val config: CollectorConfig = CollectorConfig(),
    private val context: String = "city",
    private val margins: Map<Signal, Double> = emptyMap(),
) {
    private val collector = WindowCollector(config, context)
    private var session: TripSession? = null
    private val frames = mutableListOf<Frame>()
    private val results = mutableListOf<FrameResult>()

    val live: LiveWindow get() = collector.live

    fun accept(event: SignalEvent): List<MonitorUpdate> = collector.accept(event).mapNotNull(::handle)

    fun tick(timeMs: Long): List<MonitorUpdate> = collector.tick(timeMs).mapNotNull(::handle)

    private fun handle(output: CollectorOutput): MonitorUpdate? = when (output) {
        is CollectorOutput.TripStarted -> {
            session = TripEngine.createSession(history, config.windowMinutes, margins)
            frames.clear()
            results.clear()
            MonitorUpdate.TripStarted(output.timeMs)
        }
        is CollectorOutput.WindowClosed -> session?.let {
            val result = it.ingest(output.frame)
            frames += output.frame
            results += result
            MonitorUpdate.WindowJudged(output.frame, result)
        }
        is CollectorOutput.TripEnded -> session?.let {
            val parked = it.ingest(output.frame)
            session = null
            MonitorUpdate.Arrived(
                TripReport(
                    context = context,
                    windowMinutes = config.windowMinutes,
                    frames = frames.toList(),
                    results = results.toList(),
                    detection = parked.detection,
                    baselines = TripEngine.baselineFor(history, context, margins),
                ),
            )
        }
    }
}
