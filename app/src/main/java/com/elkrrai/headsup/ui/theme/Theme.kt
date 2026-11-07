package com.elkrrai.headsup.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The app is always dark with neon accents: it's the look we chose, so no dynamic colour. */
private val NeonColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OnNeon,
    primaryContainer = NeonCyanSoft,
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = OnNeon,
    secondaryContainer = NeonCyanSoft,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonAmber,
    onTertiary = OnNeon,
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = Muted,
    surfaceTint = NeonCyan,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = SurfaceNav,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceCard,
    surfaceContainerHighest = SurfaceCard,
    outline = OutlineStrong,
    outlineVariant = Outline,
    error = ErrorNeon,
    onError = OnNeon,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFFB4C4),
)

@Composable
fun HeadsUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = Typography,
        content = content
    )
}

/** A soft neon halo around a button or card. */
fun Modifier.glow(color: Color = NeonCyan, radius: Dp = 18.dp, shape: Shape = CircleShape): Modifier =
    shadow(radius, shape, ambientColor = color.copy(alpha = 0.6f), spotColor = color.copy(alpha = 0.6f))
