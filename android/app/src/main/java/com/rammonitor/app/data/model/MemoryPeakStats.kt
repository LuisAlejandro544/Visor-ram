package com.rammonitor.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo inmutable que registra los picos históricos y estadísticas acumuladas
 * durante una sesión activa de monitoreo de memoria RAM.
 * 
 * ¿Para qué sirve?
 * - Permite al usuario saber cuál fue el momento de máxima exigencia de RAM en su teléfono
 *   (por ejemplo, durante una partida de juego o multitarea pesada).
 * - Registra la hora exacta del pico, la oscilación entre el consumo mínimo y máximo,
 *   y cuál fue la aplicación individual que generó el mayor pico de consumo.
 * 
 * @property peakUsedPercentage Porcentaje máximo de RAM registrado (0 a 100%).
 * @property peakUsedRamBytes Cantidad máxima de RAM física usada en bytes.
 * @property peakTimestamp Marca de tiempo (milisegundos) en que ocurrió el pico máximo.
 * @property minUsedPercentage Porcentaje mínimo de RAM registrado en la sesión.
 * @property minUsedRamBytes Cantidad mínima de RAM física usada en bytes.
 * @property sessionStartTime Hora en que inició la sesión de monitoreo.
 * @property sampleCount Cantidad de lecturas procesadas durante la sesión.
 * @property topPeakAppName Nombre legible de la app con el consumo pico individual más alto.
 * @property topPeakAppPackage Nombre del paquete de la app con mayor pico.
 * @property topPeakAppRamBytes Consumo de RAM en bytes del pico más alto de esa app.
 */
data class MemoryPeakStats(
    val peakUsedPercentage: Float = 0f,
    val peakUsedRamBytes: Long = 0L,
    val peakTimestamp: Long = 0L,
    val minUsedPercentage: Float = 100f,
    val minUsedRamBytes: Long = Long.MAX_VALUE,
    val sessionStartTime: Long = System.currentTimeMillis(),
    val sampleCount: Long = 0L,
    val topPeakAppName: String? = null,
    val topPeakAppPackage: String? = null,
    val topPeakAppRamBytes: Long = 0L
) {
    /** Convierte el pico de bytes a formato legible en Gigabytes (ej: 6.84 GB) */
    fun peakUsedInGb(): String = String.format(Locale.US, "%.2f GB", peakUsedRamBytes / (1024.0 * 1024.0 * 1024.0))

    /** Convierte el consumo mínimo de la sesión a Gigabytes */
    fun minUsedInGb(): String {
        if (minUsedRamBytes == Long.MAX_VALUE || minUsedRamBytes <= 0L) return "0.00 GB"
        return String.format(Locale.US, "%.2f GB", minUsedRamBytes / (1024.0 * 1024.0 * 1024.0))
    }

    /** Formatea la hora exacta del pico en formato HH:mm:ss */
    fun formattedPeakTime(): String {
        if (peakTimestamp <= 0L) return "--:--:--"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(peakTimestamp))
    }

    /** Calcula la duración total de la sesión en formato legible (ej: "18m 20s" o "1h 12m") */
    fun formattedSessionDuration(): String {
        val elapsedSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000L).coerceAtLeast(0L)
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** Formatea el pico de la app individual más pesada en MB o GB */
    fun formattedTopPeakAppRam(): String {
        if (topPeakAppRamBytes <= 0L) return "0 MB"
        val mb = topPeakAppRamBytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /** Diferencia porcentual entre el consumo mínimo y el pico máximo alcanzado */
    fun ramDeltaPercentage(): Float = (peakUsedPercentage - if (minUsedPercentage > 100f) 0f else minUsedPercentage).coerceAtLeast(0f)

    companion object {
        val EMPTY = MemoryPeakStats()
    }
}
