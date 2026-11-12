package com.rammonitor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rammonitor.app.data.model.MemoryPeakStats
import com.rammonitor.app.data.monitor.MemoryReader
import com.rammonitor.app.data.shizuku.ShizukuManager
import com.rammonitor.app.service.RamMonitorService
import com.rammonitor.app.ui.navigation.Screen
import com.rammonitor.app.ui.screens.DashboardScreen
import com.rammonitor.app.ui.screens.ProcessListScreen
import com.rammonitor.app.ui.screens.SettingsScreen
import com.rammonitor.app.ui.screens.ShizukuGateScreen
import com.rammonitor.app.ui.theme.DarkBackground
import com.rammonitor.app.ui.theme.DarkBorder
import com.rammonitor.app.ui.theme.DarkSurface
import com.rammonitor.app.ui.theme.RAMMonitorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Actividad Principal (Entry Point) de RAM Monitor Pro.
 * 
 * Lógica y arquitectura:
 * 1. Inicializa y vincula el Binder de Shizuku ([ShizukuManager.init]).
 * 2. Si Shizuku no está conectado o el permiso no está otorgado, muestra [ShizukuGateScreen]
 *    bloqueando el acceso hasta que se verifiquen los privilegios ADB/Root.
 * 3. Al autorizar Shizuku, ejecuta automáticamente la concesión de permisos privilegiados
 *    a través de IPackageManager (PACKAGE_USAGE_STATS).
 * 4. Gestiona la barra de navegación inferior, ciclo de vida del servicio y telemetría de picos.
 */
class MainActivity : ComponentActivity() {

    // Solicitud de permiso de notificaciones para Android 13 (API 33+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos el gestor de Shizuku
        ShizukuManager.init()

        checkNotificationPermission()

        setContent {
            RAMMonitorTheme {
                MainAppScaffold()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.cleanup()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    fun MainAppScaffold() {
        val shizukuState by ShizukuManager.state.collectAsStateWithLifecycle()

        // Si Shizuku no está conectado o el permiso no está otorgado, se bloquea la app
        // con la pantalla de guía y vinculación de Shizuku
        if (!shizukuState.isPrivileged) {
            ShizukuGateScreen(
                shizukuState = shizukuState,
                onRetry = { ShizukuManager.updateState() }
            )
            return
        }

        // Una vez concedido Shizuku, auto-otorgamos permisos de sistema (como PACKAGE_USAGE_STATS)
        // ejecutando la lógica Binder / IPackageManager solicitada
        val scope = rememberCoroutineScope()
        LaunchedEffect(shizukuState.isPrivileged) {
            if (shizukuState.isPrivileged) {
                scope.launch {
                    ShizukuManager.grantRuntimePermission(
                        packageName = packageName,
                        permissionName = "android.permission.PACKAGE_USAGE_STATS",
                        userId = 0
                    )
                }
            }
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Recolectamos el estado reactivo del servicio
        val serviceMemoryStats by RamMonitorService.memoryState.collectAsStateWithLifecycle()
        val servicePeakStats by RamMonitorService.peakStatsState.collectAsStateWithLifecycle()
        val serviceProcesses by RamMonitorService.processListState.collectAsStateWithLifecycle()
        val isServiceRunning by RamMonitorService.isServiceRunning.collectAsStateWithLifecycle()

        // Estado local en caso de que el servicio esté pausado
        var localMemoryStats by remember { mutableStateOf(serviceMemoryStats) }
        var localPeakStats by remember { mutableStateOf(servicePeakStats) }
        var localProcesses by remember { mutableStateOf(serviceProcesses) }
        val localAppPeakCache = remember { mutableMapOf<String, Long>() }

        val context = this@MainActivity
        val memoryReader = remember { MemoryReader(context) }

        // Bucle en corrutina que actualiza lecturas si el servicio en segundo plano está apagado
        LaunchedEffect(isServiceRunning) {
            if (!isServiceRunning) {
                while (isActive) {
                    val stats = memoryReader.readSystemMemory()
                    localMemoryStats = stats

                    val procs = memoryReader.readRunningProcesses(stats.totalRamBytes, localAppPeakCache)
                    procs.forEach { p ->
                        val prev = localAppPeakCache[p.packageName] ?: 0L
                        if (p.ramUsageBytes > prev) localAppPeakCache[p.packageName] = p.ramUsageBytes
                    }
                    localProcesses = procs

                    // Registro de picos en modo local
                    val prevPeak = localPeakStats
                    val isFirst = prevPeak.sampleCount == 0L
                    val isNewPeak = isFirst || stats.usedRamBytes >= prevPeak.peakUsedRamBytes
                    val newPeakBytes = if (isFirst) stats.usedRamBytes else maxOf(prevPeak.peakUsedRamBytes, stats.usedRamBytes)
                    val newPeakPct = if (isNewPeak) stats.usedPercentage else prevPeak.peakUsedPercentage
                    val newPeakTs = if (isNewPeak) stats.timestamp else prevPeak.peakTimestamp
                    val newMinBytes = if (isFirst) stats.usedRamBytes else minOf(prevPeak.minUsedRamBytes, stats.usedRamBytes)
                    val newMinPct = if (isFirst) stats.usedPercentage else minOf(prevPeak.minUsedPercentage, stats.usedPercentage)
                    val topApp = procs.maxByOrNull { it.peakRamBytes }

                    localPeakStats = prevPeak.copy(
                        peakUsedPercentage = newPeakPct,
                        peakUsedRamBytes = newPeakBytes,
                        peakTimestamp = if (newPeakTs == 0L) stats.timestamp else newPeakTs,
                        minUsedPercentage = newMinPct,
                        minUsedRamBytes = newMinBytes,
                        sampleCount = prevPeak.sampleCount + 1,
                        topPeakAppName = topApp?.appName ?: prevPeak.topPeakAppName,
                        topPeakAppPackage = topApp?.packageName ?: prevPeak.topPeakAppPackage,
                        topPeakAppRamBytes = topApp?.peakRamBytes ?: prevPeak.topPeakAppRamBytes
                    )

                    delay(2000L)
                }
            }
        }

        val activeStats = if (isServiceRunning) serviceMemoryStats else localMemoryStats
        val activePeakStats = if (isServiceRunning) servicePeakStats else localPeakStats
        val activeProcesses = if (isServiceRunning) serviceProcesses else localProcesses

        val screens = listOf(Screen.Dashboard, Screen.ProcessList, Screen.Settings)

        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = NavigationBarDefaults.Elevation
                ) {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        memoryStats = activeStats,
                        peakStats = activePeakStats,
                        processes = activeProcesses,
                        isServiceRunning = isServiceRunning,
                        onResetPeak = {
                            if (isServiceRunning) {
                                RamMonitorService.resetPeakStats()
                            } else {
                                localAppPeakCache.clear()
                                localPeakStats = MemoryPeakStats(sessionStartTime = System.currentTimeMillis())
                            }
                        },
                        onNavigateToProcesses = {
                            navController.navigate(Screen.ProcessList.route)
                        }
                    )
                }

                composable(Screen.ProcessList.route) {
                    ProcessListScreen(
                        processes = activeProcesses,
                        totalRamBytes = activeStats.totalRamBytes
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(memoryStats = activeStats)
                }
            }
        }
    }
}
