package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StealthDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = CarbonBackground,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = TextPrimaryDark,
    secondary = RegenEmerald,
    onSecondary = CarbonBackground,
    secondaryContainer = RegenEmeraldDim,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = InverterAmber,
    onTertiary = CarbonBackground,
    tertiaryContainer = InverterAmberDim,
    onTertiaryContainer = TextPrimaryDark,
    error = TrackCrimson,
    onError = Color.White,
    errorContainer = TrackCrimsonDim,
    onErrorContainer = Color(0xFFFFD9DE),
    background = CarbonBackground,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = CarbonBorder,
    outlineVariant = Color(0xFF1E293B)
)

private val DaylightTrackColorScheme = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = Color.White,
    primaryContainer = CobaltBlueDim,
    onPrimaryContainer = TextPrimaryLight,
    secondary = DaylightEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = TextPrimaryLight,
    tertiary = DaylightAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = TextPrimaryLight,
    error = DaylightCrimson,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = DaylightBackground,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = DaylightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

val CockpitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun S3xyCockpitTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StealthDarkColorScheme else DaylightTrackColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CockpitShapes,
        content = content
    )
}
