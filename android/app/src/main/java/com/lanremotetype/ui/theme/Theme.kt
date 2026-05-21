package com.lanremotetype.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LiquidColorScheme = darkColorScheme(
    primary = LiquidPrimary,
    primaryContainer = LiquidPrimaryDark,
    onPrimary = Color.White,
    secondary = LiquidSecondary,
    tertiary = LiquidTeal,
    error = LiquidRed,
    background = LiquidBackground,
    surface = LiquidSurface,
    onBackground = LiquidOnSurface,
    onSurface = LiquidOnSurface,
    surfaceVariant = LiquidSurfaceLight,
    onSurfaceVariant = LiquidOnSurfaceSecondary,
)

@Composable
fun LANRemoteTypeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = LiquidBackground.toArgb()
            window.navigationBarColor = LiquidBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = LiquidColorScheme,
        typography = Typography,
        content = content
    )
}
