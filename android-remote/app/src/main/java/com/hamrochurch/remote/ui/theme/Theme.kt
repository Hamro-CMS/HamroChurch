package com.hamrochurch.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SanctuaryBg = Color(0xFF070D19)
val SanctuarySurface = Color(0xFF0B1324)
val SanctuaryCard = Color(0xFF101B30)
val SanctuaryBorder = Color(0xFF1C2F4F)
val HamroPink = Color(0xFF0EA5E9)
val HamroPinkDark = Color(0xFF0284C7)
val HamroCyan = Color(0xFF38BDF8)
val LiveGreen = Color(0xFF22C55E)
val WarnAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFFEEF6FF)
val TextSecondary = Color(0xFF94A3B8)

private val SanctuaryColorScheme = darkColorScheme(
    primary = HamroPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0C2D4E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = HamroCyan,
    onSecondary = Color(0xFF042F36),
    secondaryContainer = Color(0xFF0E3452),
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
