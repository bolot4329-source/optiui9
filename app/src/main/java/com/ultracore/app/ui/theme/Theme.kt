package com.ultracore.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val UltraCoreColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = AmoledBlack,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = NeonCyan,
    secondary = NeonGreen,
    onSecondary = AmoledBlack,
    secondaryContainer = CardBorder,
    onSecondaryContainer = TextPrimary,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = AmoledBlack,
    onSurface = TextPrimary,
    surfaceVariant = CardBlack,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = BarBlack,
    surfaceContainer = BarBlack,
    surfaceContainerHigh = CardBlack,
    surfaceContainerHighest = CardBorder,
    outline = CardBorder,
    outlineVariant = CardBorder,
    error = NeonRed,
    onError = AmoledBlack,
)

/** Squircle-like radii. */
val CardRadius = 24.dp

private val UltraCoreShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(CardRadius),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun UltraCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UltraCoreColors,
        shapes = UltraCoreShapes,
        typography = Typography(),
        content = content,
    )
}
