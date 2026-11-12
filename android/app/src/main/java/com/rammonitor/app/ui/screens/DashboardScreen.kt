package com.rammonitor.app.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rammonitor.app.data.model.MemoryStats
import com.rammonitor.app.data.model.ProcessInfo
import com.rammonitor.app.service.RamMonitorService
import com.rammonitor.app.ui.theme.*

/**
 * Pantalla principal de Monitoreo (Dashboard).
 * 
 * Lógica y componentes:
 * 1. Indicador circular y barra de progreso animada con el % de RAM en uso.
 * 2. Cuadrícula de tarjetas con métricas técnicas (Total, Usada, Libre, Caché/Buffers y Swap ZRAM).
 * 3. Control para encender o apagar el servicio en segundo plano (Foreground Service).
 * 4. Resumen de las 3 apps que más memoria consumen con enlace directo a la lista completa.
 * 5. Botón de optimización que llama a `killBackgroundProcesses` del sistema para liberar procesos inactivos.
 */
@Composable
fun DashboardScreen(
    memoryStats: MemoryStats,
    processes: List<ProcessInfo>,
    isServiceRunning: Boolean,
    onNavigateToProcesses: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Color dinámico según la carga de memoria
    val statusColor by animateColorAsState(
        targetValue = when {
            memoryStats.usedPercentage >= 85f -> RedCritical
            memoryStats.usedPercentage >= 65f -> YellowWarning
            else -> GreenOptimal
        },
        animationSpec = tween(500),
        label = "statusColor"
    )

    val animatedPercentage by animateFloatAsState(
        targetValue = memoryStats.usedPercentage,
        animationSpec = tween(600),
        label = "animatedPercentage"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RAM Monitor Pro",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Monitoreo en tiempo real del kernel",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Interruptor del servicio en segundo plano
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isServiceRunning) "Segundo Plano ON" else "OFF",
                    color = if (isServiceRunning) CyanPrimary else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { active ->
                        if (active) {
                            RamMonitorService.start(context)
                        } else {
                            RamMonitorService.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = DarkSurface
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta central: Medidor principal de RAM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CONSUMO GLOBAL DE RAM",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Círculo central con porcentaje y valor en GB
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(statusColor.copy(alpha = 0.25f), DarkSurfaceElevated)
                            )
                        )
                        .border(3.dp, statusColor.copy(alpha = 0.8f), CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${animatedPercentage.toInt()}%",
                            color = statusColor,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${memoryStats.usedInGb()} / ${memoryStats.totalInGb()}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Libre: ${memoryStats.availableInGb()}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Barra lineal de progreso
                LinearProgressIndicator(
                    progress = { animatedPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = DarkSurfaceElevated,
                )

                // Alerta si la memoria está por encima de 85%
                if (memoryStats.usedPercentage >= 85f || memoryStats.isLowMemory) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RedCritical.copy(alpha = 0.15f))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta",
                            tint = RedCritical,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Presión de RAM alta: Android puede cerrar apps en segundo plano.",
                            color = RedCritical,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón de liberación rápida de procesos en caché
        Button(
            onClick = {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                // Cierra procesos en segundo plano inactivos permitidos por el SO
                processes.filter { !it.isSystemApp && it.importance == "En caché" }.forEach {
                    am.killBackgroundProcesses(it.packageName)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Optimizar RAM (Cerrar procesos en caché)",
                color = DarkBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Desglose técnico de la memoria
        Text(
            text = "DETALLES DEL SISTEMA",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Total RAM",
                value = memoryStats.totalInGb(),
                subtitle = "Física instalada",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "En Uso",
                value = memoryStats.usedInGb(),
                subtitle = "Apps + Sistema",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Disponible",
                value = memoryStats.availableInGb(),
                subtitle = "Inmediata",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "ZRAM (Swap)",
                value = memoryStats.swapUsedInMb(),
                subtitle = "Comprimida",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Top 3 Apps que más RAM consumen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOP CONSUMO DE RAM",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onNavigateToProcesses) {
                Text("Ver todos (${processes.size})", color = CyanPrimary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val topProcesses = processes.take(3)
        if (topProcesses.isEmpty()) {
            Text(
                text = "Cargando procesos activos...",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            topProcesses.forEach { process ->
                ProcessCompactCard(process = process)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * Tarjeta individual para mostrar una métrica técnica de memoria.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = TextMuted, fontSize = 10.sp)
        }
    }
}

/**
 * Fila compacta que resume el consumo de una app en el Dashboard.
 */
@Composable
fun ProcessCompactCard(process: ProcessInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = process.appName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "${process.packageName} • PID: ${process.pid}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = process.formattedRam(),
                    color = CyanGlow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.1f", process.ramPercentage)}% RAM",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
