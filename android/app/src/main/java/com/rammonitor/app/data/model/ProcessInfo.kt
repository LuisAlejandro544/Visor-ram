package com.rammonitor.app.data.model

/**
 * Modelo que representa el consumo de memoria y estado de un proceso o aplicación individual.
 * 
 * ¿Para qué sirve?
 * - Permite identificar cuáles aplicaciones están consumiendo más RAM en el dispositivo.
 * - Soporta ordenamiento por MB, filtrado de aplicaciones del sistema vs instaladas por el usuario,
 *   y búsqueda por nombre.
 * 
 * @property pid Identificador único del proceso en el kernel de Linux (Process ID).
 * @property processName Nombre de proceso reportado por el sistema (ej: "com.android.chrome").
 * @property packageName Nombre del paquete de la aplicación asociada.
 * @property appName Nombre legible para el usuario (ej: "Google Chrome").
 * @property ramUsageBytes Memoria PSS (Proportional Set Size) consumida en bytes.
 * @property ramPercentage Porcentaje de la memoria física total que consume este proceso.
 * @property isSystemApp True si la app reside en /system o /product, False si es descargada por el usuario.
 * @property importance Nivel de importancia de Android (Foreground, Visible, Service, Cached).
 * @property pssKb PSS en Kilobytes para cálculos precisos.
 * @property peakRamBytes Pico máximo de consumo en bytes registrado para este proceso durante la sesión.
 */
data class ProcessInfo(
    val pid: Int,
    val processName: String,
    val packageName: String,
    val appName: String,
    val ramUsageBytes: Long,
    val ramPercentage: Float,
    val isSystemApp: Boolean = false,
    val importance: String = "Background",
    val pssKb: Int = 0,
    val peakRamBytes: Long = ramUsageBytes
) {
    /** Formato amigable en Megabytes (ej: "340.5 MB") */
    fun ramInMb(): String = String.format("%.1f MB", ramUsageBytes / (1024.0 * 1024.0))

    /** Formato amigable en Gigabytes si supera 1 GB */
    fun formattedRam(): String {
        val mb = ramUsageBytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    /** Formato amigable del pico de consumo registrado para este proceso */
    fun formattedPeakRam(): String {
        val mb = peakRamBytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
