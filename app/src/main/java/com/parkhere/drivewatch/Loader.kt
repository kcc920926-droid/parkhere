package com.parkhere.drivewatch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Waiting, in the character's own way: 아방이 (the launcher icon's car) blinks its turn signals left,
 * right, left… and bobs a little, like a car waiting to pull out. With animations off it sits still.
 * Positions are on the icon's 108 grid after its 0.75 scale (ic_launcher_foreground.xml).
 */
@Composable
fun AbangLoader(size: Dp, modifier: Modifier = Modifier) {
    val still = Motion.scale == 0f
    val t = rememberInfiniteTransition(label = "abang")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (still) 1 else Motion.ms(900), easing = LinearEasing), RepeatMode.Restart), label = "blink")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (still) 1 else Motion.ms(450)), RepeatMode.Reverse), label = "bob")
    val unit = size.value / 108f
    // The car fills about half of the icon grid; zoom so it fills the loader instead.
    Box(modifier.size(size).graphicsLayer { scaleX = 1.6f; scaleY = 1.6f; translationY = if (still) 0f else -bob * unit * 2.5f * density }) {
        Image(painterResource(R.drawable.ic_launcher_foreground), "기다리는 중", Modifier.size(size))
        // Cover both painted signals, then light one side at a time.
        val left = still || phase < 0.5f
        Signal(33.4f, 65.5f, unit, if (left) Color(0xFFFF9A1F) else Color(0xFFFFD9A8))
        Signal(74.6f, 65.5f, unit, if (!left || still) Color(0xFFFF9A1F) else Color(0xFFFFD9A8))
    }
}

@Composable
private fun Signal(cx: Float, cy: Float, unit: Float, color: Color) {
    val r = 2.6f
    Box(Modifier.offset(((cx - r) * unit).dp, ((cy - r) * unit).dp).size((2 * r * unit).dp).background(color, CircleShape))
}
