package com.uzuu.signbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Luôn dùng nền tối tương phản cao, không đổi theo dark mode hệ thống
// để hai người đối diện đều đọc rõ trong mọi điều kiện.
private val HighContrastColors = darkColorScheme(
    primary = AccentYellow,
    onPrimary = Black,
    secondary = AccentCyan,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = SurfaceDark,
    onSurface = White,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = White,
    outline = OutlineGray,
    error = ErrorRed,
    onError = Black,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastColors,
        typography = AppTypography,
        content = content,
    )
}
