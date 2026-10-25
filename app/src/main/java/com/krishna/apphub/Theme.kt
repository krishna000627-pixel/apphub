package com.krishna.apphub

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** v71: true = the white theme. Set by HubTheme before the content is composed; every colour below reads it, so a change recomposes what shows them. */
object Pal { var light by mutableStateOf(false) }

val Bg: Color get() = if (Pal.light) Color(0xFFF3F7F5) else Color(0xFF050B0A)
val Surf: Color get() = if (Pal.light) Color(0xFFFFFFFF) else Color(0xFF0B1513)
val Line: Color get() = if (Pal.light) Color(0xFFCCD8D3) else Color(0xFF1F3B34)
val Green: Color get() = if (Pal.light) Color(0xFF0A8F52) else Color(0xFF19E68C)
val Cyan: Color get() = if (Pal.light) Color(0xFF0B7C99) else Color(0xFF22D3EE)
val Red: Color get() = if (Pal.light) Color(0xFFD32F2F) else Color(0xFFFF5252)
val Amber: Color get() = if (Pal.light) Color(0xFFB36A00) else Color(0xFFFFB74D)
val Muted: Color get() = if (Pal.light) Color(0xFF5B6B66) else Color(0xFF8AA39B)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF19E68C), onPrimary = Color.Black, secondary = Color(0xFF22D3EE), background = Color(0xFF050B0A), surface = Color(0xFF0B1513),
    onSurface = Color(0xFFE6F2EE), onBackground = Color(0xFFE6F2EE), error = Color(0xFFFF5252),
    surfaceVariant = Color(0xFF12201D), outline = Color(0xFF1F3B34)
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF0A8F52), onPrimary = Color.Black, secondary = Color(0xFF0B7C99), background = Color(0xFFF3F7F5), surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14211D), onBackground = Color(0xFF14211D), error = Color(0xFFD32F2F),
    surfaceVariant = Color(0xFFE8EFEC), outline = Color(0xFFCCD8D3)
)

private fun mono(): Typography {
    val t = Typography(); val m = FontFamily.Monospace
    return t.copy(
        headlineMedium = t.headlineMedium.copy(fontFamily = m), titleLarge = t.titleLarge.copy(fontFamily = m),
        titleMedium = t.titleMedium.copy(fontFamily = m), bodyLarge = t.bodyLarge.copy(fontFamily = m),
        bodyMedium = t.bodyMedium.copy(fontFamily = m), bodySmall = t.bodySmall.copy(fontFamily = m),
        labelLarge = t.labelLarge.copy(fontFamily = m), labelMedium = t.labelMedium.copy(fontFamily = m)
    )
}

/** [light] = the white theme. Also flips the status bar and navigation bar icons and the window background, so they match the app, not the phone. */
@Composable fun HubTheme(light: Boolean = false, content: @Composable () -> Unit) {
    Pal.light = light
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
            w.setBackgroundDrawable(ColorDrawable(Bg.toArgb()))
        }
    }
    MaterialTheme(colorScheme = if (light) lightScheme else darkScheme, typography = mono(), content = content)
}
