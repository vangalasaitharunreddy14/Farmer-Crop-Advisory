package com.chennuri.farm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FarmGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = FarmLightMintBg,
    onPrimaryContainer = FarmGreenDark,
    secondary = FarmGreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = FarmLightMintBg,
    onSecondaryContainer = FarmGreenDark,
    background = FarmPaleMintBg,
    onBackground = FarmTextDark,
    surface = FarmSurface,
    onSurface = FarmTextDark,
    surfaceVariant = FarmLightMintBg,
    onSurfaceVariant = FarmTextMuted,
    error = FarmRedIcon,
    errorContainer = FarmRedBg,
    onError = Color.White,
    onErrorContainer = FarmRedIcon
)

private val DarkColorScheme = darkColorScheme(
    primary = FarmGreenSecondary,
    onPrimary = Color.Black,
    primaryContainer = FarmGreenDark,
    onPrimaryContainer = FarmLightMintBg,
    background = Color(0xFF0F1F12),
    onBackground = Color(0xFFE2EBE2),
    surface = Color(0xFF152619),
    onSurface = Color(0xFFE2EBE2)
)

@Composable
fun FarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Farmer-friendly theme with rich green accents, disabled dynamicColor to preserve UI accuracy
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}