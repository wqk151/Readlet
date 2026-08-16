package com.readlet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color.White,
    tertiary = Blue,
    onTertiary = Color.White,
    error = Red,
    background = PaperLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Muted,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF0C3B24),
    secondary = Color(0xFFE0B15F),
    onSecondary = Color(0xFF3A2A08),
    tertiary = Color(0xFF8FB5E3),
    error = Color(0xFFE08A82),
    background = PaperDark,
    onBackground = Color(0xFFE8E6DF),
    surface = SurfaceDark,
    onSurface = Color(0xFFE8E6DF),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = MutedDark,
    outline = OutlineDark,
)

@Composable
fun ReadletTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
