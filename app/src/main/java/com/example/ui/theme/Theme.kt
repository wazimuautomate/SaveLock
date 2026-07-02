package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SaveLockPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = SaveLockPrimaryDark,
    secondary = SaveLockAmber,
    onSecondary = DarkOnSurface,
    tertiary = SaveLockAmber,
    error = SaveLockRed,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnBackground
)

private val LightColorScheme = lightColorScheme(
    primary = SaveLockPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = SaveLockPrimaryDark,
    secondary = SaveLockAmber,
    onSecondary = LightOnSurface,
    tertiary = SaveLockAmber,
    error = SaveLockRed,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnBackground
)

@Composable
fun SaveLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Keep MyApplicationTheme for backward compatibility and test runner
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Ignored to strictly use SaveLock color palette
    content: @Composable () -> Unit
) {
    SaveLockTheme(darkTheme = darkTheme, content = content)
}
