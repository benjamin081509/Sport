package de.vitalcoach.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.vitalcoach.core.Tone

object Palette {
    val Good = Color(0xFF1FA463)
    val Ok = Color(0xFF7CB342)
    val Warn = Color(0xFFF59E0B)
    val Bad = Color(0xFFE5484D)
    val Neutral = Color(0xFF64748B)
    val Blue = Color(0xFF2F6FED)
    val Purple = Color(0xFF8B5CF6)
    val Teal = Color(0xFF14B8A6)
    val Orange = Color(0xFFF97316)

    // Schlafphasen
    val Deep = Color(0xFF3730A3)
    val Light = Color(0xFF60A5FA)
    val Rem = Color(0xFFA855F7)
    val Awake = Color(0xFFF59E0B)

    // Herzfrequenzzonen 0–5
    val Zones = listOf(
        Color(0xFF94A3B8),
        Color(0xFF60A5FA),
        Color(0xFF22C55E),
        Color(0xFFEAB308),
        Color(0xFFF97316),
        Color(0xFFEF4444),
    )
}

fun Tone.color(): Color = when (this) {
    Tone.GOOD -> Palette.Good
    Tone.OK -> Palette.Ok
    Tone.WARN -> Palette.Warn
    Tone.BAD -> Palette.Bad
    Tone.NEUTRAL -> Palette.Neutral
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0B2E6B),
    secondary = Color(0xFF14B8A6),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF5B6474),
    outline = Color(0xFFD5DAE3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF0B1B3F),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF222833),
    onSurfaceVariant = Color(0xFFA3ACBA),
    outline = Color(0xFF333B48),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun VitalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
