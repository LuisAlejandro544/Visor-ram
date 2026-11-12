package com.rammonitor.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rammonitor.app.MainActivity
import com.rammonitor.app.R
import com.rammonitor.app.data.model.MemoryPeakStats
import com.rammonitor.app.data.model.MemoryStats
import com.rammonitor.app.data.model.ProcessInfo
import com.rammonitor.app.data.monitor.MemoryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en segundo plano (Foreground Service) de bajo impacto de consumo.
 * 
 * ¿Para qué sirve?
 * - Se ejecuta silenciosamente en segundo plano sin drenar batería.
 * - Mantiene actualizada la notificación en tiempo real con el porcentaje y GB de RAM consumidos.
 * - Publica flujos de estado reactivos ([memoryState], [peakStatsState] y [processListState]) para que la interfaz Compose
 *   se actualice instantáneamente sin recargas.
 * - Registra con precisión los picos máximos de RAM alcanzados durante la sesión.
 * - Utiliza un [SupervisorJob] y [Dispatchers.Default] para no comprometer nunca el hilo de la UI.
 */
class RamMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var monitorJob: Job? = null

    private lateinit var memoryReader: MemoryReader
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "ram_monitor_service_channel"
        const val NOTIFICATION_ID = 1001

        // Estado global observable por la interfaz de usuario
        private val _memoryState = MutableStateFlow(MemoryStats.EMPTY)
        val memoryState: StateFlow<MemoryStats> = _memoryState.asStateFlow()

        // Picos históricos de consumo registrados durante el monitoreo
        private val _peakStatsState = MutableStateFlow(MemoryPeakStats.EMPTY)
        val peakStatsState: StateFlow<MemoryPeakStats> = _peakStatsState.asStateFlow()

        // Caché en memoria para retener el pico individual de cada paquete durante la sesión
        private val appPeakCache = mutableMapOf<String, Long>()

        private val _processListState = MutableStateFlow<List<ProcessInfo>>(emptyList())
        val processListState: StateFlow<List<ProcessInfo>> = _processListState.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        // Intervalo de muestreo configurable (3 segundos por defecto para equilibrio óptimo)
        var sampleIntervalMillis: Long = 3000L

        /** Reinicia los registros de picos de la sesión para comenzar una nueva medición */
        fun resetPeakStats() {
            synchronized(appPeakCache) {
                appPeakCache.clear()
            }
            _peakStatsState.value = MemoryPeakStats(
                sessionStartTime = System.currentTimeMillis()
            )
        }

        /** Inicia el servicio en segundo plano */
        fun start(context: Context) {
            val intent = Intent(context, RamMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Detiene el servicio */
        fun stop(context: Context) {
            val intent = Intent(context, RamMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        memoryReader = MemoryReader(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = buildNotification("Iniciando monitor de RAM...", "Calculando estado...")
        startForeground(NOTIFICATION_ID, initialNotification)
        _isServiceRunning.value = true

        startMonitoringLoop()

        // START_STICKY: Si el sistema cierra el servicio por baja memoria extrema,
        // lo recreará automáticamente cuando la memoria se estabilice.
        return START_STICKY
    }

    /**
     * Bucle de monitoreo ejecutado mediante Corrutinas.
     * Lee la memoria y procesos cada [sampleIntervalMillis] milisegundos sin consumir CPU innecesaria.
     */
    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val stats = memoryReader.readSystemMemory()
                    _memoryState.value = stats

                    // Pasamos la caché de picos para preservar y enriquecer los picos por app
                    val processes = synchronized(appPeakCache) {
                        memoryReader.readRunningProcesses(stats.totalRamBytes, appPeakCache)
                    }
                    _processListState.value = processes

                    // Actualizamos la caché de picos individuales de cada app
                    synchronized(appPeakCache) {
                        processes.forEach { proc ->
                            val prev = appPeakCache[proc.packageName] ?: 0L
                            if (proc.ramUsageBytes > prev) {
                                appPeakCache[proc.packageName] = proc.ramUsageBytes
                            }
                        }
                    }

                    // Calculamos y publicamos los nuevos picos de la sesión
                    val prevPeak = _peakStatsState.value
                    val isFirstSample = prevPeak.sampleCount == 0L
                    val isNewPeak = isFirstSample || stats.usedRamBytes >= prevPeak.peakUsedRamBytes
                    val newPeakBytes = if (isFirstSample) stats.usedRamBytes else maxOf(prevPeak.peakUsedRamBytes, stats.usedRamBytes)
                    val newPeakPercentage = if (isNewPeak) stats.usedPercentage else prevPeak.peakUsedPercentage
                    val newPeakTimestamp = if (isNewPeak) stats.timestamp else prevPeak.peakTimestamp

                    val newMinBytes = if (isFirstSample) stats.usedRamBytes else minOf(prevPeak.minUsedRamBytes, stats.usedRamBytes)
                    val newMinPercentage = if (isFirstSample) stats.usedPercentage else minOf(prevPeak.minUsedPercentage, stats.usedPercentage)

                    val topAppByPeak = processes.maxByOrNull { it.peakRamBytes }
                    val topPeakAppName = topAppByPeak?.appName ?: prevPeak.topPeakAppName
                    val topPeakAppPackage = topAppByPeak?.packageName ?: prevPeak.topPeakAppPackage
                    val topPeakAppRamBytes = topAppByPeak?.peakRamBytes ?: prevPeak.topPeakAppRamBytes

                    _peakStatsState.value = prevPeak.copy(
                        peakUsedPercentage = newPeakPercentage,
                        peakUsedRamBytes = newPeakBytes,
                        peakTimestamp = if (newPeakTimestamp == 0L) stats.timestamp else newPeakTimestamp,
                        minUsedPercentage = newMinPercentage,
                        minUsedRamBytes = newMinBytes,
                        sampleCount = prevPeak.sampleCount + 1,
                        topPeakAppName = topPeakAppName,
                        topPeakAppPackage = topPeakAppPackage,
                        topPeakAppRamBytes = topPeakAppRamBytes
                    )

                    // Actualizamos la notificación del sistema
                    val title = "RAM en uso: ${stats.usedPercentage.toInt()}% (${stats.usedInGb()} / ${stats.totalInGb()})"
                    val peakInfo = if (_peakStatsState.value.peakUsedPercentage > 0f) " • Pico: ${_peakStatsState.value.peakUsedPercentage.toInt()}%" else ""
                    val topApp = processes.firstOrNull()?.let { "Mayor: ${it.appName} (${it.ramInMb()})$peakInfo" } ?: "Libre: ${stats.availableInGb()}"
                    
                    val notification = buildNotification(title, topApp)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } catch (_: Exception) {
                    // Silenciamos excepciones temporales durante transiciones del sistema
                }

                delay(sampleIntervalMillis)
            }
        }
    }

    /**
     * Construye la notificación persistente de bajo consumo con acceso directo a la app.
     */
    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_ram_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Prioridad baja para no interrumpir con sonido
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        monitorJob?.cancel()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
