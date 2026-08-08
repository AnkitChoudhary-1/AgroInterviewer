package com.agro.interviewer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = InterviewBlue,
    onPrimary = Color.White,
    primaryContainer = InterviewBlue.copy(alpha = 0.12f),
    onPrimaryContainer = InterviewBlue,
    secondary = InterviewGreen,
    onSecondary = Color.White,
    secondaryContainer = InterviewGreen.copy(alpha = 0.12f),
    onSecondaryContainer = InterviewGreen,
    error = InterviewRed,
    onError = Color.White,
    errorContainer = InterviewRed.copy(alpha = 0.12f),
    onErrorContainer = InterviewRed,
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private val DarkColorScheme = darkColorScheme(
    primary = InterviewBlueDark,
    onPrimary = Color(0xFF002884),
    primaryContainer = InterviewBlue.copy(alpha = 0.24f),
    onPrimaryContainer = InterviewBlueDark,
    secondary = InterviewGreenDark,
    onSecondary = Color(0xFF003918),
    secondaryContainer = InterviewGreen.copy(alpha = 0.24f),
    onSecondaryContainer = InterviewGreenDark,
    error = InterviewRedDark,
    onError = Color(0xFF690005),
    errorContainer = InterviewRed.copy(alpha = 0.24f),
    onErrorContainer = InterviewRedDark,
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

@Composable
fun AgroInterviewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
