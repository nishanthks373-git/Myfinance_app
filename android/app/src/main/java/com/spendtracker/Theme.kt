package com.spendtracker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Fixed brand palette so the app looks the same on every phone. */
object Brand {
    val Indigo = Color(0xFF4F46E5)
    val Violet = Color(0xFF7C3AED)
    val Pink = Color(0xFFDB2777)
    val Teal = Color(0xFF0D9488)
    val Green = Color(0xFF16A34A)
    val Amber = Color(0xFFF59E0B)
    val Orange = Color(0xFFEA580C)
    val Blue = Color(0xFF2563EB)
    val Red = Color(0xFFDC2626)
    val Gray = Color(0xFF9CA3AF)

    private val palette = listOf(Indigo, Teal, Orange, Blue, Pink, Violet, Green, Amber)

    /** Stable colour per name, used for avatars. */
    fun colorFor(name: String): Color =
        palette[(name.lowercase().hashCode() and 0x7fffffff) % palette.size]

    /** Higher return rate, greener colour. */
    fun rateColor(rate: Double): Color = when {
        rate >= 0.05 -> Green
        rate >= 0.02 -> Teal
        rate > 0.0 -> Amber
        else -> Gray
    }
}

private val LightColors = lightColorScheme(
    primary = Brand.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B6B),
    secondary = Brand.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Brand.Amber,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFF4F5FF),
    onBackground = Color(0xFF1B1C2E),
    surface = Color.White,
    onSurface = Color(0xFF1B1C2E),
    surfaceVariant = Color(0xFFECEEFB),
    onSurfaceVariant = Color(0xFF454A66),
    error = Brand.Red,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B6B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFCD34D),
    background = Color(0xFF0F1020),
    onBackground = Color(0xFFE8E9F8),
    surface = Color(0xFF1A1B30),
    onSurface = Color(0xFFE8E9F8),
    surfaceVariant = Color(0xFF2A2C47),
    onSurfaceVariant = Color(0xFFC5C8E6),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun SpendTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
