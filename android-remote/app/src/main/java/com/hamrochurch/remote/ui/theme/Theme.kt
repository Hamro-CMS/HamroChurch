package com.hamrochurch.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SanctuaryBg = Color(0xFF0B0E17)
val SanctuarySurface = Color(0xFF151928)
val SanctuaryCard = Color(0xFF1C2236)
val SanctuaryBorder = Color(0xFF2B3350)
val HamroPink = Color(0xFFE6349C)
val HamroPinkDark = Color(0xFFB31E74)
val HamroCyan = Color(0xFF22D3EE)
val LiveGreen = Color(0xFF22C55E)
val WarnAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

private val SanctuaryColorScheme = darkColorScheme(
    primary = HamroPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1235),
    onPrimaryContainer = Color(0xFFFFD8EC),
    secondary = HamroCyan,
    onSecondary = Color(0xFF042F36),
    secondaryContainer = Color(0xFF103742),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = LiveGreen,
    background = SanctuaryBg,
    onBackground = TextPrimary,
    surface = SanctuarySurface,
    onSurface = TextPrimary,
    surfaceVariant = SanctuaryCard,
    onSurfaceVariant = TextSecondary,
    outline = SanctuaryBorder,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun HamroChurchRemoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SanctuaryColorScheme,
        content = content
    )
}
