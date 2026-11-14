package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val McsDarkColorScheme = darkColorScheme(
    primary = EmeraldCore,
    onPrimary = OnEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldCore,
    secondary = DiamondCyan,
    onSecondary = OnDiamond,
    secondaryContainer = DiamondContainer,
    onSecondaryContainer = DiamondCyan,
    tertiary = AmberGold,
    onTertiary = OnAmber,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberGold,
    error = RedstoneCrimson,
    onError = OnRedstone,
    errorContainer = RedstoneContainer,
    onErrorContainer = RedstoneCrimson,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = BasaltSurface,
    onSurface = TextPrimary,
    surfaceVariant = BasaltElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = BasaltSurface,
    surfaceContainerHigh = BasaltElevated,
    surfaceContainerHighest = BasaltHighest,
    outline = EtchedBorder,
    outlineVariant = ColorTokens.SubtleDivider
)

object ColorTokens {
    val SubtleDivider = EtchedBorder.copy(alpha = 0.55f)
}

private val McsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun McsHostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = McsDarkColorScheme,
        typography = McsTypography,
        shapes = McsShapes,
        content = content
    )
}
