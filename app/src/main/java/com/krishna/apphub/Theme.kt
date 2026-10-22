package com.krishna.apphub

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val Bg = Color(0xFF050B0A)
val Surf = Color(0xFF0B1513)
val Line = Color(0xFF1F3B34)
val Green = Color(0xFF19E68C)
val Cyan = Color(0xFF22D3EE)
val Red = Color(0xFFFF5252)
val Amber = Color(0xFFFFB74D)
val Muted = Color(0xFF8AA39B)

private val scheme = darkColorScheme(
    primary = Green, onPrimary = Color.Black, secondary = Cyan, background = Bg, surface = Surf,
    onSurface = Color(0xFFE6F2EE), onBackground = Color(0xFFE6F2EE), error = Red,
    surfaceVariant = Color(0xFF12201D), outline = Line
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

@Composable fun HubTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = mono(), content = content)
