package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AeroEmerald,
    secondary = AeroSecondaryGreen,
    tertiary = AeroCyanAccent,
    background = AeroBackground,
    surface = AeroSurface,
    onPrimary = AeroBackground,
    onSecondary = AeroBackground,
    onTertiary = AeroBackground,
    onBackground = AeroTextPrimary,
    onSurface = AeroTextPrimary
)

@Composable
fun AeroShareTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = AeroBackground.toArgb()
                window.navigationBarColor = AeroBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
