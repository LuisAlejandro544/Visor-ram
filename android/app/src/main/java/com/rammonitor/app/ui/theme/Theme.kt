package com.rammonitor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema Material 3 para la aplicación.
 * Aplica los colores de sistema y estilos tipográficos en toda la jerarquía de vistas Compose.
 */
private val DarkColorPalette = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkBackground,
    secondary = BlueAccent,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

@Composable
fun RAMMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorPalette,
        content = content
    )
}
