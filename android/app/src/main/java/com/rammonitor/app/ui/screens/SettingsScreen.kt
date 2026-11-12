package com.rammonitor.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.VpnKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rammonitor.app.data.model.MemoryStats
import com.rammonitor.app.data.shizuku.ShizukuManager
import com.rammonitor.app.service.RamMonitorService
import com.rammonitor.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Pantalla de Configuración, Ajustes de Rendimiento e Información de Arquitecturas.
 * 
 * Lógica y capacidades:
 * 1. Control del estado de enlace privilegiado de Shizuku (ADB UID 2000 o Root UID 0).
 * 2. Re-ejecución de concesión de permisos de sistema (PACKAGE_USAGE_STATS).
 * 3. Ajuste del intervalo de refresco en segundo plano (1s, 3s, 5s, 10s) para optimizar batería.
 * 4. Umbral de alerta por alta presión de RAM (ej: 80% o 85%).
 * 5. Detección de la arquitectura del procesador (32-bit armeabi-v7a / 64-bit arm64-v8a / x86).
 */
@Composable
fun SettingsScreen(memoryStats: MemoryStats) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val shizukuState by ShizukuManager.state.collectAsStateWithLifecycle()
    var selectedInterval by remember { mutableStateOf(RamMonitorService.sampleIntervalMillis) }
    var alertThreshold by remember { mutableFloatStateOf(85f) }
    var permissionStatusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Ajustes del Monitor",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // Sección 0: Estado del Enlace Shizuku y Permisos
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (shizukuState.isPrivileged) CyanPrimary.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Entorno Privilegiado Shizuku",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                InfoRow(
                    label = "Modo de Privilegios Activo",
                    value = shizukuState.modeLabel
                )
                InfoRow(
                    label = "Versión API Shizuku",
                    value = if (shizukuState.version > 0) "v${shizukuState.version}" else "Desconocida"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = ShizukuManager.grantRuntimePermission(
                                packageName = "com.rammonitor.app",
                                permissionName = "android.permission.PACKAGE_USAGE_STATS",
                                userId = 0
                            )
                            permissionStatusMessage = if (success) {
                                "¡Permiso PACKAGE_USAGE_STATS concedido vía IPackageManager con éxito!"
                            } else {
                                "Comando ejecutado. Verifica en Ajustes si fue concedido."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Re-otorgar Permisos de Sistema (IPackageManager)",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                permissionStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        color = GreenOptimal,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Sección 1: Frecuencia de muestreo en segundo plano
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Frecuencia de Muestreo",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A mayor tiempo, menor consumo de batería del servicio en segundo plano.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val intervals = listOf(
                        1000L to "1s (Rápido)",
                        3000L to "3s (Equilibrado)",
                        5000L to "5s (Ahorro)",
                        10000L to "10s (Ultra)"
                    )

                    intervals.chunked(2).forEach { rowIntervals ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowIntervals.forEach { (millis, label) ->
                                Button(
                                    onClick = {
                                        selectedInterval = millis
                                        RamMonitorService.sampleIntervalMillis = millis
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedInterval == millis) CyanPrimary else DarkSurfaceElevated
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selectedInterval == millis) DarkBackground else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sección 2: Umbral de Alerta de RAM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Umbral de Alerta de RAM",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Avisar en notificación cuando el consumo supere este valor:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "70%", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = "${alertThreshold.toInt()}%",
                        color = CyanGlow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "95%", color = TextMuted, fontSize = 12.sp)
                }

                Slider(
                    value = alertThreshold,
                    onValueChange = { alertThreshold = it },
                    valueRange = 70f..95f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = DarkSurfaceElevated
                    )
                )
            }
        }

        // Sección 3: Arquitectura de CPU y compatibilidad
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Arquitectura y Entorno del Dispositivo",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val abis = Build.SUPPORTED_ABIS.joinToString(", ")
                val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

                InfoRow(label = "Arquitecturas ABI soportadas", value = abis)
                InfoRow(label = "Modo de ejecución", value = if (is64Bit) "64 bits nativo (arm64/x86_64)" else "32 bits (armeabi-v7a)")
                InfoRow(label = "Versión de Android", value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow(label = "Fabricante y Modelo", value = "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoRow(label = "Umbral OOM del Kernel", value = String.format("%.2f MB", memoryStats.thresholdBytes / (1024.0 * 1024.0)))
                InfoRow(label = "Distribución", value = "APK Autónomo (Uptodown / F-Droid / Directo)")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Divider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}
