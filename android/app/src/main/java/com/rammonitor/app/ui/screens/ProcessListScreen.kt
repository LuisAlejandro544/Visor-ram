package com.rammonitor.app.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.rammonitor.app.data.shizuku.ShizukuManager
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rammonitor.app.data.model.ProcessInfo
import com.rammonitor.app.ui.theme.*

/**
 * Pantalla de Lista de Procesos y consumo detallado de RAM.
 * 
 * Lógica y capacidades:
 * 1. Buscador en vivo por nombre de app o package id.
 * 2. Filtros rápidos para apps de usuario vs procesos del sistema o apps pesadas (>100MB).
 * 3. Selección y apertura de un modal descriptivo al tocar cualquier proceso.
 * 4. Acceso directo a la información del sistema en Ajustes de Android para forzar detención si el usuario lo requiere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    processes: List<ProcessInfo>,
    totalRamBytes: Long
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ProcessFilter.ALL) }
    var selectedProcess by remember { mutableStateOf<ProcessInfo?>(null) }

    // Filtrado y ordenamiento de procesos en tiempo real
    val filteredProcesses = remember(processes, searchQuery, selectedFilter) {
        processes.filter { proc ->
            val matchesQuery = proc.appName.contains(searchQuery, ignoreCase = true) ||
                    proc.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                ProcessFilter.ALL -> true
                ProcessFilter.USER_ONLY -> !proc.isSystemApp
                ProcessFilter.SYSTEM_ONLY -> proc.isSystemApp
                ProcessFilter.HIGH_RAM -> proc.ramUsageBytes >= (100 * 1024 * 1024)
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Procesos en Memoria",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredProcesses.size} procesos activos analizados",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Campo de búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por app o paquete...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Chips de filtrado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == ProcessFilter.ALL,
                        onClick = { selectedFilter = ProcessFilter.ALL },
                        label = { Text("Todos", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = DarkBackground,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == ProcessFilter.USER_ONLY,
                        onClick = { selectedFilter = ProcessFilter.USER_ONLY },
                        label = { Text("Usuario", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = DarkBackground,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == ProcessFilter.HIGH_RAM,
                        onClick = { selectedFilter = ProcessFilter.HIGH_RAM },
                        label = { Text("> 100 MB", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = DarkBackground,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == ProcessFilter.SYSTEM_ONLY,
                        onClick = { selectedFilter = ProcessFilter.SYSTEM_ONLY },
                        label = { Text("Sistema", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = DarkBackground,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        if (filteredProcesses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No se encontraron procesos coincidentes.",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProcesses, key = { it.pid }) { process ->
                    ProcessCard(
                        process = process,
                        onClick = { selectedProcess = process }
                    )
                }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Modal con detalles del proceso seleccionado
    selectedProcess?.let { proc ->
        AlertDialog(
            onDismissRequest = { selectedProcess = null },
            containerColor = DarkSurface,
            title = {
                Text(text = proc.appName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Paquete: ${proc.packageName}", color = TextSecondary, fontSize = 12.sp)
                    Text(text = "PID: ${proc.pid}", color = TextSecondary, fontSize = 12.sp)
                    Text(text = "Memoria PSS actual: ${proc.formattedRam()} (${proc.pssKb} KB)", color = CyanGlow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Pico registrado en sesión: ${proc.formattedPeakRam()}",
                        color = if (proc.peakRamBytes > proc.ramUsageBytes) YellowWarning else CyanGlow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = "Tipo: ${if (proc.isSystemApp) "App de Sistema" else "App de Usuario"}", color = TextSecondary, fontSize = 12.sp)
                    Text(text = "Prioridad Android: ${proc.importance}", color = TextSecondary, fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Acción Privilegiada Shizuku:",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Puedes forzar el cierre completo del paquete mediante privilegios ADB sin cerrar el resto del sistema.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Botón privilegiado Shizuku am force-stop
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                ShizukuManager.forceStopPackage(proc.packageName)
                            }
                            selectedProcess = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedCritical)
                    ) {
                        Text("Forzar Cierre (Shizuku)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Botón Ajustes del sistema
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", proc.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                            selectedProcess = null
                        }
                    ) {
                        Text("Ajustes", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProcess = null }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Tarjeta individual para mostrar un proceso en la lista completa.
 */
@Composable
fun ProcessCard(
    process: ProcessInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = process.appName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "${process.packageName} • PID ${process.pid}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = process.formattedRam(),
                        color = if (process.ramUsageBytes > 200 * 1024 * 1024) YellowWarning else CyanGlow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (process.peakRamBytes > process.ramUsageBytes) {
                        Text(
                            text = "▲ Pico: ${process.formattedPeakRam()}",
                            color = YellowWarning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "${String.format("%.1f", process.ramPercentage)}% RAM",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra visual de porcentaje de RAM consumida por esta app
            LinearProgressIndicator(
                progress = { (process.ramPercentage / 25f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (process.ramUsageBytes > 200 * 1024 * 1024) YellowWarning else CyanPrimary,
                trackColor = DarkSurfaceElevated
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (process.isSystemApp) "Sistema" else "Usuario",
                    color = if (process.isSystemApp) TextMuted else BlueAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = process.importance,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

enum class ProcessFilter {
    ALL,
    USER_ONLY,
    SYSTEM_ONLY,
    HIGH_RAM
}
