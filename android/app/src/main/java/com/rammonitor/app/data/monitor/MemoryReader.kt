package com.rammonitor.app.data.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import android.os.Process
import com.rammonitor.app.data.model.MemoryStats
import com.rammonitor.app.data.model.ProcessInfo
import com.rammonitor.app.data.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Lector y analizador de memoria de bajo consumo.
 * 
 * Lógica y arquitectura:
 * 1. Usa corrutinas con [Dispatchers.IO] para que ninguna lectura de `/proc/meminfo` o cálculos
 *    de memoria pesados se ejecuten en el hilo principal (UI Thread).
 * 2. Lee directamente `/proc/meminfo` para obtener métricas exactas del kernel de Linux:
 *    MemTotal, MemFree, MemAvailable, Buffers, Cached, SwapTotal, SwapFree.
 * 3. Si Shizuku está conectado y autorizado, consulta la lista GLOBAL de procesos del sistema operativo
 *    mediante 'dumpsys meminfo' / 'ps -A' superando la restricción de Android 8-15 que solo devuelve la app propia.
 * 4. Implementa una caché [appDetailsCache] para no saturar el Binder de PackageManager y reducir drásticamente
 *    el consumo de memoria y CPU propio.
 */
class MemoryReader(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    // Caché en memoria para evitar llamadas IPC repetitivas a PackageManager en cada ciclo
    private val appDetailsCache = ConcurrentHashMap<String, Pair<String, Boolean>>()

    /**
     * Obtiene el estado global de la memoria RAM del dispositivo de forma asíncrona.
     * Diseñado para tener un coste de CPU inferior al 0.1% por lectura.
     */
    suspend fun readSystemMemory(): MemoryStats = withContext(Dispatchers.IO) {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        var totalBytes = memInfo.totalMem
        var availableBytes = memInfo.availMem
        val thresholdBytes = memInfo.threshold
        val isLowMemory = memInfo.lowMemory

        var swapTotalBytes = 0L
        var swapFreeBytes = 0L
        var cachedBytes = 0L

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
        } catch (_: Exception) {}

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
     * Obtiene la lista completa de procesos activos y su consumo de memoria RAM.
     * 
     * Estrategia de compatibilidad:
     * 1. Si Shizuku está activo: Consulta 'dumpsys meminfo' con privilegios ADB/Root,
     *    obteniendo el PSS de TODAS las apps del dispositivo (Chrome, WhatsApp, juegos, sistema).
     * 2. Fallback: Si Shizuku aún no está conectado, consulta [ActivityManager.runningAppProcesses].
     */
    suspend fun readRunningProcesses(
        totalRamBytes: Long,
        appPeakMap: Map<String, Long> = emptyMap()
    ): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val myPid = Process.myPid()
        var myPrivateDirtyKb = 0

        // Calculamos la memoria privada real (Private Dirty) de nuestra propia app
        try {
            val selfMem = activityManager.getProcessMemoryInfo(intArrayOf(myPid))
            myPrivateDirtyKb = selfMem.firstOrNull()?.totalPrivateDirty ?: 0
        } catch (_: Exception) {}

        // Intentamos obtener procesos mediante Shizuku primero si tiene privilegios
        if (ShizukuManager.state.value.isPrivileged) {
            val shizukuDumps = ShizukuManager.fetchRunningProcessesViaShizuku()
            if (shizukuDumps.isNotEmpty()) {
                val results = mutableListOf<ProcessInfo>()
                for (dump in shizukuDumps) {
                    val pssBytes = dump.pssKb * 1024L
                    val (appName, isSystem) = getAppDetails(dump.packageName)

                    val percentage = if (totalRamBytes > 0) {
                        ((pssBytes.toDouble() / totalRamBytes.toDouble()) * 100.0).toFloat()
                    } else {
                        0f
                    }

                    val cachedPeak = appPeakMap[dump.packageName] ?: pssBytes
                    val effectivePeak = maxOf(cachedPeak, pssBytes)
                    val privDirty = if (dump.pid == myPid) myPrivateDirtyKb else 0

                    val importanceLabel = if (dump.isForeground) {
                        "Primer plano"
                    } else if (isSystem) {
                        "Servicio del sistema"
                    } else {
                        "Segundo plano"
                    }

                    results.add(
                        ProcessInfo(
                            pid = dump.pid,
                            processName = dump.packageName,
                            packageName = dump.packageName,
                            appName = appName,
                            ramUsageBytes = pssBytes,
                            ramPercentage = percentage,
                            isSystemApp = isSystem,
                            importance = importanceLabel,
                            pssKb = dump.pssKb,
                            peakRamBytes = effectivePeak,
                            privateDirtyKb = privDirty
                        )
                    )
                }
                return@withContext results.sortedByDescending { it.ramUsageBytes }
            }
        }

        // Fallback clásico mediante ActivityManager cuando Shizuku no está disponible
        val runningProcesses = activityManager.runningAppProcesses ?: return@withContext emptyList()
        val pids = runningProcesses.map { it.pid }.toIntArray()
        if (pids.isEmpty()) return@withContext emptyList()

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

            val cachedPeak = appPeakMap[pkgName] ?: pssBytes
            val effectivePeak = maxOf(cachedPeak, pssBytes)
            val privDirty = if (appProcess.pid == myPid) (mem?.totalPrivateDirty ?: myPrivateDirtyKb) else 0

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
                    pssKb = pssKb,
                    peakRamBytes = effectivePeak,
                    privateDirtyKb = privDirty
                )
            )
        }

        results.sortedByDescending { it.ramUsageBytes }
    }

    /**
     * Resuelve el nombre amigable de la aplicación y si es del sistema o de usuario.
     * Utiliza [appDetailsCache] para evitar miles de llamadas IPC redundantes por minuto.
     */
    private fun getAppDetails(packageName: String): Pair<String, Boolean> {
        return appDetailsCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                Pair(label, isSystem)
            } catch (_: PackageManager.NameNotFoundException) {
                Pair(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }, false)
            }
        }
    }
}
