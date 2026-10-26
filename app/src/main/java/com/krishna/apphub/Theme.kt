package com.krishna.apphub

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** v74: one colour set per theme. [green] is the main accent of the theme (buttons, titles, switches). */
class Palette(val name: String, val light: Boolean, val bg: Color, val surf: Color, val line: Color, val green: Color, val cyan: Color,
              val red: Color, val amber: Color, val muted: Color, val text: Color, val surfVar: Color)

object Themes {
    private fun c(h: Long) = Color(0xFF000000 or h)
    val dark = Palette("Dark", false, c(0x050B0A), c(0x0B1513), c(0x1F3B34), c(0x19E68C), c(0x22D3EE), c(0xFF5252), c(0xFFB74D), c(0x8AA39B), c(0xE6F2EE), c(0x12201D))
    val white = Palette("White", true, c(0xF3F7F5), c(0xFFFFFF), c(0xCCD8D3), c(0x0A8F52), c(0x0B7C99), c(0xD32F2F), c(0xB36A00), c(0x5B6B66), c(0x14211D), c(0xE8EFEC))
    val amoled = Palette("AMOLED black", false, c(0x000000), c(0x0A0A0A), c(0x222222), c(0x19E68C), c(0x22D3EE), c(0xFF5252), c(0xFFB74D), c(0x8AA39B), c(0xEDEDED), c(0x141414))
    val midnight = Palette("Midnight blue", false, c(0x070B16), c(0x0E1526), c(0x22304F), c(0x5CA8FF), c(0x7DE3F4), c(0xFF5252), c(0xFFB74D), c(0x8C9BB5), c(0xE4ECFA), c(0x141C32))
    val sunset = Palette("Sunset", false, c(0x120A06), c(0x1D120B), c(0x40291A), c(0xFFB74D), c(0xFF8A65), c(0xFF5252), c(0xFFD54F), c(0xB09A8A), c(0xF6E9DF), c(0x28180F))
    val violet = Palette("Violet", false, c(0x0C0814), c(0x150F22), c(0x32244D), c(0xB388FF), c(0x80DEEA), c(0xFF5252), c(0xFFB74D), c(0x9D93B8), c(0xEDE6F7), c(0x1E1633))
    val slate = Palette("Slate", false, c(0x101417), c(0x182026), c(0x2C3944), c(0x4DD0B8), c(0x90CAF9), c(0xFF5252), c(0xFFB74D), c(0x8EA0AB), c(0xE3EAEE), c(0x202B33))
    val paper = Palette("Paper", true, c(0xFAF6EE), c(0xFFFFFF), c(0xE2D9C6), c(0xC27A00), c(0x00796B), c(0xC62828), c(0x9A6700), c(0x7A6E58), c(0x2B2418), c(0xF0E9DA))

    /** Saved theme number -> palette. 2 = follow the phone (Dark or White), so it has no palette of its own. */
    val byMode: List<Palette?> = listOf(dark, white, null, amoled, midnight, sunset, violet, slate, paper)
    val names = listOf("Dark", "White", "System", "AMOLED black", "Midnight blue", "Sunset", "Violet", "Slate", "Paper")

    fun of(mode: Int, systemDark: Boolean): Palette = if (mode == 2) (if (systemDark) dark else white) else byMode.getOrNull(mode) ?: dark

    /** Accent choices (0 = the theme's own). Pair = on a dark theme, on a light theme. */
    val accents: List<Pair<Color, Color>?> = listOf(null, c(0x22D3EE) to c(0x0B7C99), c(0xFFB74D) to c(0xB36A00), c(0xFF80AB) to c(0xC2185B), c(0xB388FF) to c(0x6A3FD1))
    val accentNames = listOf("Theme", "Cyan", "Amber", "Pink", "Violet")
    /** Text size steps: Small, Normal, Large. */
    val textScale = listOf(0.9f, 1f, 1.15f)
}

/** The palette in use. Set by HubTheme before the content is composed; every colour below reads it, so a change recomposes what shows them. */
object Pal {
    var p by mutableStateOf(Themes.dark)
    var accent by mutableIntStateOf(0)
    val green: Color get() = Themes.accents.getOrNull(accent)?.let { if (p.light) it.second else it.first } ?: p.green
}

val Bg: Color get() = Pal.p.bg
val Surf: Color get() = Pal.p.surf
val Line: Color get() = Pal.p.line
val Green: Color get() = Pal.green
val Cyan: Color get() = Pal.p.cyan
val Red: Color get() = Pal.p.red
val Amber: Color get() = Pal.p.amber
val Muted: Color get() = Pal.p.muted

private fun scheme(p: Palette, accent: Color) =
    if (p.light) lightColorScheme(primary = accent, onPrimary = Color.Black, secondary = p.cyan, background = p.bg, surface = p.surf,
        onSurface = p.text, onBackground = p.text, error = p.red, surfaceVariant = p.surfVar, outline = p.line)
    else darkColorScheme(primary = accent, onPrimary = Color.Black, secondary = p.cyan, background = p.bg, surface = p.surf,
        onSurface = p.text, onBackground = p.text, error = p.red, surfaceVariant = p.surfVar, outline = p.line)

private fun mono(): Typography {
    val t = Typography(); val m = FontFamily.Monospace
    return t.copy(
        headlineMedium = t.headlineMedium.copy(fontFamily = m), titleLarge = t.titleLarge.copy(fontFamily = m),
        titleMedium = t.titleMedium.copy(fontFamily = m), bodyLarge = t.bodyLarge.copy(fontFamily = m),
        bodyMedium = t.bodyMedium.copy(fontFamily = m), bodySmall = t.bodySmall.copy(fontFamily = m),
        labelLarge = t.labelLarge.copy(fontFamily = m), labelMedium = t.labelMedium.copy(fontFamily = m)
    )
}

/** [mode] = saved theme number, [accent] / [textSize] = saved add-ons. Also flips the status bar and navigation bar icons and the window background, so they match the app, not the phone. */
@Composable fun HubTheme(mode: Int, accent: Int, textSize: Int, systemDark: Boolean, content: @Composable () -> Unit) {
    val p = Themes.of(mode, systemDark)
    Pal.p = p; Pal.accent = accent
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = p.light; isAppearanceLightNavigationBars = p.light }
            w.setBackgroundDrawable(ColorDrawable(p.bg.toArgb()))
        }
    }
    val d = LocalDensity.current
    val f = Themes.textScale.getOrElse(textSize) { 1f }
    MaterialTheme(colorScheme = scheme(p, Pal.green), typography = mono()) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * f), content = content)
    }
}
