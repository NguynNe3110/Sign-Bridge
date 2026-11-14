package com.uzuu.signbridge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Keep the established dark conversation surface consistent for both readers.
private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = SelectedSurface,
    onPrimaryContainer = OnSelectedSurface,
    secondary = TextSecondary,
    onSecondary = Background,
    secondaryContainer = SelectedSurface,
    onSecondaryContainer = OnSelectedSurface,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    surfaceDim = Background,
    surfaceBright = SurfaceRaised,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceRaised,
    outline = Outline,
    outlineVariant = Divider,
    error = Error,
    onError = Background,
    errorContainer = ErrorSurface,
    onErrorContainer = OnErrorSurface,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(Dimens.CornerRadius),
            medium = RoundedCornerShape(Dimens.CornerRadius),
            large = RoundedCornerShape(Dimens.CornerRadius),
        ),
        content = content,
    )
}
