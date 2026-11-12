package com.rammonitor.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de navegación principales de la aplicación.
 * Separa la app en pantallas modulares claramente definidas.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Monitoreo", Icons.Default.DeveloperBoard)
    object ProcessList : Screen("processes", "Procesos", Icons.Default.ListAlt)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Tune)
}
