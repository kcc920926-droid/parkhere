package com.parkhere.drivewatch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Colour tokens, taken from the Pleos Connect UI Design Guide (Foundations > Colors, design guide 2.1.5):
 * grayscale basic_00…basic_900 in light and dark, surface_basic/high, text primary/tertiary
 * (#131417 or white at 100 % / 64 %), button_filled (basic_600) and the informative colours
 * active #02C265/#00E074, positive #0064FF/#0082FF, negative #FE3D16/#FF4C28.
 * https://document.pleos.ai/en/docs/connect/guide/docs-design/foundations/colors
 *
 * Deviations, all for the guide's own "contrast at least 4.5:1" rule (Introduction): in the light theme
 * green and red text use darker shades of the same hue (ok, talk); the Pleos values stay for fills
 * (okFill, talkFill). Amber for "관찰" and the purple demo colours are DriveWatch's own: Pleos has no
 * caution colour, and the demo panel must not look like part of the product.
 */
data class Palette(
    val bg: Color, val panel: Color, val raised: Color, val ink: Color, val dim: Color,
    val primary: Color, val onPrimary: Color, val accent: Color,
    val ok: Color, val okFill: Color, val warn: Color, val warnFill: Color, val talk: Color, val talkFill: Color, val onChip: Color,
    val demo: Color, val demoBg: Color, val demoButton: Color, val demoBorder: Color,
    val track: Color, val danger: Color, val chosen: Color, val alertBg: Color,
)

val NightPalette = Palette(
    bg = Color(0xFF131417), panel = Color(0xFF1D1E21), raised = Color(0xFF313236), ink = Color.White, dim = Color(0xA3FFFFFF),
    primary = Color(0xFFE0E1E6), onPrimary = Color(0xFF131417), accent = Color(0xFF0082FF),
    ok = Color(0xFF00E074), okFill = Color(0xFF00E074), warn = Color(0xFFF5B85C), warnFill = Color(0xFFF5B85C),
    talk = Color(0xFFFF4C28), talkFill = Color(0xFFFF4C28), onChip = Color(0xFF131417),
    demo = Color(0xFFD9C7FF), demoBg = Color(0xFF2A2040), demoButton = Color(0xFF3B2E5C), demoBorder = Color(0xFF8C6BD9),
    track = Color(0xFF686A72), danger = Color(0xFFFF4C28), chosen = Color(0xFF0B2A4F), alertBg = Color(0xFF2B2110),
)

val DayPalette = Palette(
    bg = Color(0xFFF7F8FA), panel = Color.White, raised = Color(0xFFEDEEF2), ink = Color(0xFF131417), dim = Color(0xA3131417),
    primary = Color(0xFF44464E), onPrimary = Color.White, accent = Color(0xFF0064FF),
    ok = Color(0xFF00873F), okFill = Color(0xFF02C265), warn = Color(0xFFA86200), warnFill = Color(0xFFF5B85C),
    talk = Color(0xFFD02C08), talkFill = Color(0xFFFE3D16), onChip = Color(0xFF131417),
    demo = Color(0xFF6A4CC2), demoBg = Color(0xFFEFE9FB), demoButton = Color(0xFFDCD0F5), demoBorder = Color(0xFF8C6BD9),
    track = Color(0xFFC7C9CE), danger = Color(0xFFD02C08), chosen = Color(0xFFE0ECFF), alertBg = Color(0xFFFFF3DF),
)

val LocalPalette = staticCompositionLocalOf { NightPalette }

/** Theme change is a slow cross-fade of every token, so day to night reads as dusk rather than a flash. */
@Composable
fun animatedPalette(night: Boolean): Palette {
    val to = if (night) NightPalette else DayPalette
    @Composable fun c(pick: (Palette) -> Color) = animateColorAsState(pick(to), tween(Motion.ms(THEME_MS)), label = "theme").value
    return Palette(
        c { it.bg }, c { it.panel }, c { it.raised }, c { it.ink }, c { it.dim },
        c { it.primary }, c { it.onPrimary }, c { it.accent },
        c { it.ok }, c { it.okFill }, c { it.warn }, c { it.warnFill }, c { it.talk }, c { it.talkFill }, c { it.onChip },
        c { it.demo }, c { it.demoBg }, c { it.demoButton }, c { it.demoBorder },
        c { it.track }, c { it.danger }, c { it.chosen }, c { it.alertBg },
    )
}

private const val THEME_MS = 600

/**
 * Asta Sans, the Pleos Connect system UI font (Design Guide > Foundations > Typography), by 42dot,
 * SIL Open Font License 1.1 (assets/licenses/AstaSans-OFL.txt). One variable file, weights 300–800.
 */
@OptIn(ExperimentalTextApi::class)
val AstaSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.asta_sans, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

/** Every Material text style in Asta Sans, so Text, text fields and labels all use it. */
fun Typography.inFamily(f: FontFamily) = Typography(
    displayLarge.copy(fontFamily = f), displayMedium.copy(fontFamily = f), displaySmall.copy(fontFamily = f),
    headlineLarge.copy(fontFamily = f), headlineMedium.copy(fontFamily = f), headlineSmall.copy(fontFamily = f),
    titleLarge.copy(fontFamily = f), titleMedium.copy(fontFamily = f), titleSmall.copy(fontFamily = f),
    bodyLarge.copy(fontFamily = f), bodyMedium.copy(fontFamily = f), bodySmall.copy(fontFamily = f),
    labelLarge.copy(fontFamily = f), labelMedium.copy(fontFamily = f), labelSmall.copy(fontFamily = f),
)

/**
 * Motion rules for the car: short, no bounce, and none at all when the system animation scale is 0
 * (Settings > Developer options, or accessibility "remove animations").
 */
object Motion {
    var scale = 1f
    const val SCREEN_MS = 250
    const val BUTTON_MS = 220
    /** A spinner shorter than this flickers; keep busy states on screen at least this long. */
    const val MIN_BUSY_MS = 600L
    fun ms(base: Int) = (base * scale).toInt()
}
