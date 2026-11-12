package com.rammonitor.app.data.model

/**
 * Modelo de datos inmutable que representa una instantánea completa del estado de la memoria RAM del sistema.
 * 
 * ¿Para qué sirve?
 * - Proporciona métricas globales de RAM física y de memoria Swap/ZRAM.
 * - Es consumido por el Dashboard, el servicio en segundo plano (Foreground Service)
 *   y las alertas de consumo elevado.
 * 
 * @property totalRamBytes Memoria física total reconocida por el kernel de Linux.
 * @property availableRamBytes Memoria disponible inmediatamente sin cerrar apps críticas.
 * @property usedRamBytes Memoria calculada en uso activo (totalRamBytes - availableRamBytes).
 * @property usedPercentage Porcentaje de memoria utilizada (0 a 100%).
 * @property thresholdBytes Umbral de baja memoria fijado por el sistema operativo Android.
 * @property isLowMemory Indica si el kernel está en estado de memoria crítica (OOM killer activo).
 * @property swapTotalBytes Espacio total configurado para ZRAM / Swap de compresión en memoria.
 * @property swapUsedBytes Cantidad de datos comprimidos actualmente en ZRAM.
 * @property cachedBytes Memoria utilizada para caché de disco y buffers del kernel (reclamable).
 * @property timestamp Marca de tiempo en milisegundos de la captura.
 */
data class MemoryStats(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val usedRamBytes: Long,
    val usedPercentage: Float,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
    val swapTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val cachedBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Convierte bytes de memoria total a formato legible en Gigabytes (ej: 7.42 GB) */
    fun totalInGb(): String = String.format("%.2f GB", totalRamBytes / (1024.0 * 1024.0 * 1024.0))

    /** Convierte bytes en uso a Gigabytes */
    fun usedInGb(): String = String.format("%.2f GB", usedRamBytes / (1024.0 * 1024.0 * 1024.0))

    /** Convierte bytes disponibles a Gigabytes */
    fun availableInGb(): String = String.format("%.2f GB", availableRamBytes / (1024.0 * 1024.0 * 1024.0))

    /** ZRAM utilizada en Megabytes */
    fun swapUsedInMb(): String = String.format("%.1f MB", swapUsedBytes / (1024.0 * 1024.0))

    companion object {
        /** Instancia vacía/inicial para evitar estados nulos en la UI */
        val EMPTY = MemoryStats(
            totalRamBytes = 0L,
            availableRamBytes = 0L,
            usedRamBytes = 0L,
            usedPercentage = 0f,
            thresholdBytes = 0L,
            isLowMemory = false
        )
    }
}
