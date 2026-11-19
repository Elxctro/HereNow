package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FlatBlackPinkColorScheme = darkColorScheme(
    primary = FlatPink,
    onPrimary = FlatPinkOnPrimary,
    primaryContainer = FlatPinkContainer,
    onPrimaryContainer = FlatPinkSoft,
    secondary = FlatPinkSoft,
    onSecondary = FlatPinkOnPrimary,
    secondaryContainer = FlatBlackElevated,
    onSecondaryContainer = FlatTextPrimary,
    tertiary = FlatPink,
    onTertiary = FlatPinkOnPrimary,
    background = FlatBlackBg,
    onBackground = FlatTextPrimary,
    surface = FlatBlackSurface,
    onSurface = FlatTextPrimary,
    surfaceVariant = FlatBlackElevated,
    onSurfaceVariant = FlatTextSecondary,
    surfaceContainerLowest = FlatBlackBg,
    surfaceContainerLow = FlatBlackSurface,
    surfaceContainer = FlatBlackSurface,
    surfaceContainerHigh = FlatBlackElevated,
    surfaceContainerHighest = FlatBlackElevated,
    outline = FlatBlackOutline,
    outlineVariant = FlatBlackOutline,
    error = FlatError,
    onError = Color.White,
    errorContainer = FlatErrorContainer,
    onErrorContainer = FlatOnErrorContainer
)

@Composable
fun HereNowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlatBlackPinkColorScheme,
        typography = AppTypography,
        content = content
    )
}
