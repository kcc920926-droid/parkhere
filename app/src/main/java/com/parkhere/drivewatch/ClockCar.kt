package com.parkhere.drivewatch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 시계 차: DriveWatch's character, a car seen from the side whose window is a gauge and whose headlight is an eye.
 * The launcher icon (res/drawable/ic_launcher_foreground.xml, source app/design/brand/clockcar-icon.svg) shows it
 * with a plain clock and never a status: the launcher cannot change it and a "대화 권장" icon would label the driver.
 * Inside the app the window tells the week three ways at once, so no one has to rely on colour alone:
 * the needle's position (left 안정, middle 관찰, right 대화 권장), the lit band's colour, and the face.
 * Same 120-grid geometry as the family page (family-server/public/clockcar.js).
 */
object ClockCarArt {
    val PURPLE = Color(0xFF6B5CF5)
    private val INK = Color(0xFF131417)
    private val FACE = Color(0xFF2A2350)
    private fun path(d: String) = PathParser().parsePathString(d).toPath()
    internal val body by lazy { path("M20 84 Q13 84 13 76 Q13 61 28 58 L34 57 Q38 30 60 28 Q82 28 88 56 L94 57 Q107 60 107 74 Q107 84 100 84 Z") }
    internal val window by lazy { path("M40 57 Q42 38 60 37 Q78 38 80 57 Z") }
    private val mouth by lazy {
        mapOf(
            State.LEARNING to path("M96 75.5 Q99 77.5 102 75.5"),
            State.STABLE to path("M95 75 Q99 78.5 103 75"),
            State.WATCH to path("M96 76 H102"),
            State.TALK to path("M95 77 Q99 73.5 103 77"),
        )
    }
    private val CENTER = Offset(60f, 55f)
    private const val R = 14f
    /** Bands in screen degrees (0° = right, clockwise): green, amber, orange-red across the top of the dial. */
    private val BANDS = listOf(Triple(State.STABLE, 180f, Color(0xFF02C265)), Triple(State.WATCH, 243f, Color(0xFFF5A623)), Triple(State.TALK, 306f, Color(0xFFFE3D16)))

    fun needle(s: State) = when (s) { State.STABLE -> 207f; State.WATCH, State.LEARNING -> 270f; State.TALK -> 333f }

    /** Draws the badge (purple rounded square) and the car on a 120 grid scaled to the canvas. */
    fun DrawScope.draw(state: State, needleDeg: Float) {
        drawRect(PURPLE)
        val k = size.width / 120f
        scale(k, k, pivot = Offset.Zero) {
            translate(3f, 1f) {
                scale(0.95f, 0.95f, pivot = Offset.Zero) { car(state, needleDeg) }
            }
        }
    }

    private fun DrawScope.car(state: State, needleDeg: Float) {
        drawPath(body, Color.White)
        for (x in listOf(36f, 84f)) {
            drawCircle(Color.White, 10f, Offset(x, 84f))
            drawCircle(PURPLE, 10f, Offset(x, 84f), style = Stroke(4f))
        }
        drawPath(window, Color(0xFFEDEEF2))
        val box = Offset(CENTER.x - R, CENTER.y - R)
        val dial = Size(2 * R, 2 * R)
        if (state == State.LEARNING) {
            drawArc(Color(0xFFB9BBC2), 180f, 180f, false, box, dial, style = Stroke(4.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3.5f))))
        } else for ((band, from, color) in BANDS) {
            drawArc(color, from, 54f, false, box, dial, alpha = if (band == state) 1f else 0.28f, style = Stroke(4.5f))
        }
        val hand = if (state == State.LEARNING) Color(0xFF686A72) else INK
        val rad = Math.toRadians(needleDeg.toDouble())
        drawLine(hand, CENTER, Offset(CENTER.x + 11f * cos(rad).toFloat(), CENTER.y + 11f * sin(rad).toFloat()), 3f, StrokeCap.Round)
        drawCircle(hand, 2.6f, CENTER)
        drawCircle(FACE, 4.2f, Offset(99f, 67f))
        drawCircle(Color.White, 1.3f, Offset(100.3f, 65.8f))
        if (state == State.TALK) drawLine(FACE, Offset(94f, 60.5f), Offset(101.5f, 62f), 1.8f, StrokeCap.Round)
        drawPath(mouth.getValue(state), FACE, style = Stroke(2f, cap = StrokeCap.Round))
    }
}

/** The character showing [state]. When the state changes, the needle swings to its new place. */
@Composable
fun ClockCar(state: State, size: Dp, modifier: Modifier = Modifier, description: String? = "운전 상태 ${state.label}") {
    val needle by animateFloatAsState(ClockCarArt.needle(state), tween(Motion.ms(600)), label = "needle")
    Canvas(modifier.size(size).clip(RoundedCornerShape(26)).let { m -> description?.let { d -> m.semantics { contentDescription = d } } ?: m }) {
        with(ClockCarArt) { draw(state, needle) }
    }
}

/**
 * Waiting, in the character's own way: the needle sweeps across the dial and back, like a gauge when the car starts.
 * With animations off it sits still in the middle.
 */
@Composable
fun ClockCarLoader(size: Dp, modifier: Modifier = Modifier) {
    val still = Motion.scale == 0f
    val t = rememberInfiniteTransition(label = "clockcar")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (still) 1 else Motion.ms(700), easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    val needle = if (still) 270f else 190f + 160f * sweep
    Canvas(modifier.size(size).clip(RoundedCornerShape(26)).semantics { contentDescription = "기다리는 중" }) {
        with(ClockCarArt) { draw(State.LEARNING, needle) }
    }
}
