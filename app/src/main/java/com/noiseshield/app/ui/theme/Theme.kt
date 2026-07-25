package com.noiseshield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.noiseshield.app.data.AppThemeMode

private val DeepTeal = Color(0xFF0B3D3E)
private val SoftMint = Color(0xFF7BC6C0)
private val Sand = Color(0xFFE8E0D5)
private val Ink = Color(0xFF122022)
private val Mist = Color(0xFFF3F6F5)

private val LightColors = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    secondary = SoftMint,
    onSecondary = Ink,
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = SoftMint,
    onPrimary = Ink,
    secondary = DeepTeal,
    onSecondary = Sand,
    background = Color(0xFF0A1213),
    onBackground = Sand,
    surface = Color(0xFF132022),
    onSurface = Sand,
)

@Composable
fun NoiseShieldTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
