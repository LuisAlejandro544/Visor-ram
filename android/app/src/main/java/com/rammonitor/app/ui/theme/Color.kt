package com.rammonitor.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores para RAM Monitor Pro.
 * Diseñado con temática oscura estilo instrumentación de hardware (Cyberpunk/Dark Slate),
 * con alto contraste para legibilidad en exteriores y bajo consumo de batería en pantallas AMOLED.
 */
val DarkBackground = Color(0xFF0A0F1D)
val DarkSurface = Color(0xFF131B2E)
val DarkSurfaceElevated = Color(0xFF1E293B)
val DarkBorder = Color(0xFF334155)

// Colores de acento dinámicos según el estado de la RAM
val CyanPrimary = Color(0xFF06B6D4)
val CyanGlow = Color(0xFF22D3EE)
val BlueAccent = Color(0xFF3B82F6)

// Semáforo de advertencia según % de consumo
val GreenOptimal = Color(0xFF10B981) // < 60%
val YellowWarning = Color(0xFFF59E0B) // 60% - 85%
val RedCritical = Color(0xFFEF4444)  // > 85%

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
