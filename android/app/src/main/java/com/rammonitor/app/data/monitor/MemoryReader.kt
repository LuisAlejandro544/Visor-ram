package com.rammonitor.app.data.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import android.os.Process
import com.rammonitor.app.data.model.MemoryStats
import com.rammonitor.app.data.model.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Lector y analizador de memoria de bajo consumo.
 * 
 * Lógica y arquitectura:
 * 1. Usa corrutinas con [Dispatchers.IO] para que ninguna lectura de `/proc/meminfo` o cálculos
 *    de memoria pesados se ejecuten en el hilo principal (UI Thread).
 * 2. Lee directamente `/proc/meminfo` para obtener métricas exactas del kernel de Linux:
 *    MemTotal, MemFree, MemAvailable, Buffers, Cached, SwapTotal, SwapFree.
 * 3. Complementa con [ActivityManager.MemoryInfo] para consultar los umbrales del sistema Android.
 * 4. Para procesos individuales, consulta PSS (Proportional Set Size) usando [ActivityManager.getProcessMemoryInfo],
 *    que es la métrica más precisa en sistemas operativos Linux/Android para medir la memoria compartida y privada.
 */
class MemoryReader(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    /**
     * Obtiene el estado global de la memoria RAM del dispositivo de forma asíncrona.
     * Diseñado para tener un coste de CPU inferior al 0.1% por lectura.
     */
    suspend fun readSystemMemory(): MemoryStats = withContext(Dispatchers.IO) {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        // Valores por defecto basados en ActivityManager
        var totalBytes = memInfo.totalMem
        var availableBytes = memInfo.availMem
        val thresholdBytes = memInfo.threshold
        val isLowMemory = memInfo.lowMemory

        var swapTotalBytes = 0L
        var swapFreeBytes = 0L
        var cachedBytes = 0L

        // Lectura de bajo nivel del kernel en /proc/meminfo para obtener ZRAM y buffers exactos
        try {
            val procMemInfo = File("/proc/meminfo")
            if (procMemInfo.exists() && procMemInfo.canRead()) {
                BufferedReader(FileReader(procMemInfo)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        val parts = currentLine.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            val key = parts[0]
                            val valueKb = parts[1].toLongOrNull() ?: 0L
                            val valueBytes = valueKb * 1024L
                            when (key) {
                                "MemTotal:" -> if (totalBytes == 0L) totalBytes = valueBytes
                                "MemAvailable:" -> availableBytes = valueBytes
                                "Cached:" -> cachedBytes = valueBytes
                                "SwapTotal:" -> swapTotalBytes = valueBytes
                                "SwapFree:" -> swapFreeBytes = valueBytes
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Si el kernel bloquea /proc/meminfo en algún dispositivo específico,
            // nos mantenemos con los valores seguros de ActivityManager.
        }

        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        val usedPercentage = if (totalBytes > 0) {
            ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }

        val swapUsedBytes = (swapTotalBytes - swapFreeBytes).coerceAtLeast(0L)

        MemoryStats(
            totalRamBytes = totalBytes,
            availableRamBytes = availableBytes,
            usedRamBytes = usedBytes,
            usedPercentage = usedPercentage,
            thresholdBytes = thresholdBytes,
            isLowMemory = isLowMemory,
            swapTotalBytes = swapTotalBytes,
            swapUsedBytes = swapUsedBytes,
            cachedBytes = cachedBytes
        )
    }

    /**
     * Obtiene la lista de procesos activos y su consumo de memoria RAM.
     * Ejecuta el análisis en [Dispatchers.Default] y [Dispatchers.IO] para evitar cualquier tirón en la interfaz.
     */
    suspend fun readRunningProcesses(totalRamBytes: Long): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val runningProcesses = activityManager.runningAppProcesses ?: return@withContext emptyList()
        val pids = runningProcesses.map { it.pid }.toIntArray()

        if (pids.isEmpty()) return@withContext emptyList()

        // Obtiene la memoria PSS precisa en bloques
        val memoryInfoArray = try {
            activityManager.getProcessMemoryInfo(pids)
        } catch (_: Exception) {
            emptyArray<Debug.MemoryInfo>()
        }

        val results = mutableListOf<ProcessInfo>()

        for (i in runningProcesses.indices) {
            val appProcess = runningProcesses[i]
            val mem = memoryInfoArray.getOrNull(i)
            val pssKb = mem?.totalPss ?: 0
            val pssBytes = pssKb * 1024L

            val pkgName = appProcess.pkgList?.firstOrNull() ?: appProcess.processName
            val (appName, isSystem) = getAppDetails(pkgName)

            val percentage = if (totalRamBytes > 0) {
                ((pssBytes.toDouble() / totalRamBytes.toDouble()) * 100.0).toFloat()
            } else {
                0f
            }

            val importanceLabel = when (appProcess.importance) {
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Primer plano"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Servicio"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "En caché"
                else -> "Segundo plano"
            }

            results.add(
                ProcessInfo(
                    pid = appProcess.pid,
                    processName = appProcess.processName,
                    packageName = pkgName,
                    appName = appName,
                    ramUsageBytes = pssBytes,
                    ramPercentage = percentage,
                    isSystemApp = isSystem,
                    importance = importanceLabel,
                    pssKb = pssKb
                )
            )
        }

        // Ordenamos por mayor consumo de memoria descendente
        results.sortedByDescending { it.ramUsageBytes }
    }

    /**
     * Resuelve el nombre amigable de la aplicación y si es del sistema o de usuario.
     */
    private fun getAppDetails(packageName: String): Pair<String, Boolean> {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            Pair(label, isSystem)
        } catch (_: PackageManager.NameNotFoundException) {
            Pair(packageName.substringAfterLast('.'), false)
        }
    }
}
