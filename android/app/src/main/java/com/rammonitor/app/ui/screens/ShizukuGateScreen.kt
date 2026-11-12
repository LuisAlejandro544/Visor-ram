package com.rammonitor.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rammonitor.app.data.shizuku.ShizukuManager
import com.rammonitor.app.data.shizuku.ShizukuState
import com.rammonitor.app.ui.theme.*

/**
 * Pantalla de control de acceso obligatorio (Gatekeeper) de Shizuku.
 * 
 * ¿Por qué es obligatoria?
 * Android restringe el acceso a la memoria PSS precisa de otras aplicaciones y limita
 * la terminación de procesos en segundo plano. Shizuku proporciona los privilegios de ADB (UID 2000)
 * o Root (UID 0) necesarios para que RAM Monitor Pro funcione como una herramienta avanzada.
 * 
 * Si el usuario no tiene PC:
 * La pantalla incluye la guía para iniciar Shizuku mediante 'Depuración inalámbrica' directamente
 * desde el teléfono móvil (Android 11+).
 */
@Composable
fun ShizukuGateScreen(
    shizukuState: ShizukuState,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Icono de estado con gradiente
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(DarkSurfaceElevated)
                .border(2.dp, if (shizukuState.isAvailable) CyanPrimary else YellowWarning, CircleShape)
        ) {
            Icon(
                imageVector = if (shizukuState.isAvailable) Icons.Default.VpnKey else Icons.Default.Shield,
                contentDescription = "Estado Shizuku",
                tint = if (shizukuState.isAvailable) CyanPrimary else YellowWarning,
                modifier = Modifier.size(44.dp)
            )
        }

        Text(
            text = "Shizuku Requerido",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Para monitorear el consumo real de RAM de todas las aplicaciones y detener procesos agresivos sin ROOT, esta aplicación requiere Shizuku.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        // Tarjeta de estado actual
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ESTADO DEL SERVICIO",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (shizukuState.isPrivileged) GreenOptimal
                                else if (shizukuState.isAvailable) YellowWarning
                                else RedCritical
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shizukuState.modeLabel,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = shizukuState.statusMessage,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Botones de acción principales
        if (shizukuState.isAvailable && !shizukuState.hasPermission) {
            // Shizuku está corriendo pero falta otorgar el permiso a esta app
            Button(
                onClick = { ShizukuManager.requestPermission() },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = DarkBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Autorizar Shizuku Ahora",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        } else if (!shizukuState.isAvailable) {
            // Shizuku no está iniciado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Abrir app de Shizuku instalada
                Button(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            // Si no la tiene instalada, abrir descarga oficial
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                            context.startActivity(browserIntent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Abrir Shizuku", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                // Botón de reintentar detección
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reintentar")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verificar")
                }
            }
        }

        // Guía sin computadora (100% en el móvil)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "¿Cómo iniciar Shizuku sin PC? (Móvil)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                InstructionStep(number = "1", title = "Conéctate a una red Wi-Fi", desc = "La depuración inalámbrica de Android requiere estar conectado a Wi-Fi (o a una zona portátil propia).")
                InstructionStep(number = "2", title = "Opciones de Desarrollador", desc = "Ve a Ajustes > Acerca del teléfono y presiona 7 veces 'Número de compilación'.")
                InstructionStep(number = "3", title = "Depuración inalámbrica", desc = "Activa 'Depuración por Wi-Fi / Inalámbrica' en Opciones de Desarrollador.")
                InstructionStep(number = "4", title = "Vincular en Shizuku", desc = "Abre Shizuku, presiona 'Vincular' y pega el código de 6 dígitos de la notificación.")
                InstructionStep(number = "5", title = "Iniciar", desc = "Presiona 'Iniciar' dentro de Shizuku y regresa a RAM Monitor Pro.")
            }
        }
    }
}

@Composable
fun InstructionStep(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.2f))
                .border(1.dp, CyanPrimary, CircleShape)
        ) {
            Text(text = number, color = CyanPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
