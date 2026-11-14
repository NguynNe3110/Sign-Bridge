package com.uzuu.signbridge.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val DarkColors: ColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkSelectedSurface,
    onPrimaryContainer = DarkOnSelectedSurface,
    secondary = DarkTextSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSelectedSurface,
    onSecondaryContainer = DarkOnSelectedSurface,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkTextSecondary,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceRaised,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceRaised,
    surfaceContainerHighest = DarkSurfaceRaised,
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    error = DarkError,
    onError = DarkBackground,
    errorContainer = DarkErrorSurface,
    onErrorContainer = DarkOnErrorSurface,
)

val LightColors: ColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightSelectedSurface,
    onPrimaryContainer = LightOnSelectedSurface,
    secondary = LightTextSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightSelectedSurface,
    onSecondaryContainer = LightOnSelectedSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    surfaceDim = LightSurfaceRaised,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightBackground,
    surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerHighest = LightSurfaceRaised,
    outline = LightOutline,
    outlineVariant = LightDivider,
    error = LightError,
    onError = LightOnAccent,
    errorContainer = LightErrorSurface,
    onErrorContainer = LightOnErrorSurface,
)

// Legacy alias if any internal references used AppColors
internal val AppColors = DarkColors

fun appColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    context: Context? = null,
): ColorScheme {
    return when {
        dynamicColor && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = if (dynamicColor) LocalContext.current else null
    val colorScheme = appColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor, context = context)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(Dimens.CornerRadius),
            medium = RoundedCornerShape(Dimens.CornerRadius),
            large = RoundedCornerShape(Dimens.CornerRadius),
        ),
        content = content,
    )
}
