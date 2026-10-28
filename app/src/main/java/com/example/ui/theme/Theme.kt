package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = MidnightNavy,
    primaryContainer = DarkCyanContainer,
    onPrimaryContainer = CyanGlow,
    secondary = InfoSky,
    onSecondary = MidnightNavy,
    background = MidnightNavy,
    onBackground = PureWhite,
    surface = CardNavy,
    onSurface = PureWhite,
    surfaceVariant = BorderNavy,
    onSurfaceVariant = MutedText,
    error = CriticalRed,
    onError = PureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = DeepCyan,
    secondary = Color(0xFF0284C7),
    onSecondary = PureWhite,
    background = OffWhite,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightCard,
    onSurfaceVariant = Color(0xFF64748B),
    error = CriticalRed,
    onError = PureWhite
)

@Composable
fun SmartMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded tech theme by default for unified look
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
