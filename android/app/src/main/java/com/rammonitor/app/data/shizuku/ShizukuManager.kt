package com.rammonitor.app.data.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Estado observable de la conexión con Shizuku o Sui.
 * 
 * @property isAvailable Indica si el servicio Shizuku está corriendo en el dispositivo.
 * @property hasPermission Indica si el usuario otorgó permiso a la app para usar Shizuku.
 * @property uid UID del proceso de Shizuku (0 para ROOT, 2000 para ADB Shell).
 * @property version Versión de la API de Shizuku reportada.
 * @property isRoot True si Shizuku está corriendo con privilegios de Root (UID 0).
 * @property statusMessage Texto explicativo para la interfaz.
 */
data class ShizukuState(
    val isAvailable: Boolean = false,
    val hasPermission: Boolean = false,
    val uid: Int = -1,
    val version: Int = 0,
    val isRoot: Boolean = false,
    val statusMessage: String = "Comprobando servicio Shizuku..."
) {
    val isPrivileged: Boolean get() = isAvailable && hasPermission
    val modeLabel: String get() = when {
        !isAvailable -> "Shizuku Desconectado"
        !hasPermission -> "Esperando Permiso"
        isRoot -> "Modo Root (UID 0)"
        else -> "Modo ADB Shell (UID 2000)"
    }
}

/**
 * Gestor centralizado de Shizuku para RAM Monitor Pro.
 * 
 * ¿Para qué sirve?
 * 1. Controla el ciclo de vida del Binder de Shizuku mediante listeners de conexión y muerte.
 * 2. Solicita el permiso especial de Shizuku al usuario.
 * 3. Permite la auto-concesión de permisos de sistema (como PACKAGE_USAGE_STATS o DUMP)
 *    mediante la llamada Binder a IPackageManager o mediante comandos pm directos con privilegios ADB.
 * 4. Permite forzar el cierre ('force-stop') de apps que acaparen la RAM sin necesidad de ROOT clásico.
 */
object ShizukuManager {

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private const val REQUEST_CODE_SHIZUKU_PERMISSION = 7001

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = ShizukuState(
            isAvailable = false,
            hasPermission = false,
            statusMessage = "El servicio de Shizuku se ha detenido o desconectado."
        )
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                updateState()
            }
        }

    /**
     * Inicializa los escuchadores del Binder de Shizuku.
     * Debe ser invocado en el inicio de la app (MainActivity o Application).
     */
    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            updateState()
        } catch (_: Exception) {
            _state.value = ShizukuState(
                isAvailable = false,
                hasPermission = false,
                statusMessage = "No se pudo enlazar con Shizuku en este dispositivo."
            )
        }
    }

    /**
     * Limpia los listeners registrados para evitar fugas de memoria.
     */
    fun cleanup() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (_: Exception) {}
    }

    /**
     * Actualiza el estado reactivo consultando las APIs de Shizuku.
     */
    fun updateState() {
        val isAlive = try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }

        if (!isAlive) {
            _state.value = ShizukuState(
                isAvailable = false,
                hasPermission = false,
                statusMessage = "Shizuku no está en ejecución. Inícialo vía Depuración inalámbrica o Root."
            )
            return
        }

        val hasPermission = try {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

        val uid = try { Shizuku.getUid() } catch (_: Exception) { -1 }
        val version = try { Shizuku.getVersion() } catch (_: Exception) { 0 }
        val isRoot = (uid == 0)

        _state.value = ShizukuState(
            isAvailable = true,
            hasPermission = hasPermission,
            uid = uid,
            version = version,
            isRoot = isRoot,
            statusMessage = if (hasPermission) {
                "Conectado a Shizuku con éxito (${if (isRoot) "Root UID 0" else "ADB UID 2000"})."
            } else {
                "Shizuku detectado. Se requiere conceder permiso para continuar."
            }
        )
    }

    /**
     * Solicita la autorización al usuario en la ventana modal de Shizuku.
     */
    fun requestPermission() {
        if (!_state.value.isAvailable) return
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
            } else {
                updateState()
            }
        } catch (_: Exception) {}
    }

    /**
     * Implementación del snippet solicitado por el usuario:
     * Concede permisos en tiempo de ejecución invocando directamente IPackageManager del sistema Android
     * envuelto mediante [ShizukuBinderWrapper] con privilegios de ADB (Shell UID 2000) o Root (UID 0).
     * 
     * ¿Por qué sirve esto?
     * En Android estándar, las apps de terceros no pueden conceder permisos protegidos como
     * PACKAGE_USAGE_STATS o DUMP. Shizuku permite invocar `grantRuntimePermission` como si estuvieras
     * ejecutando comandos desde una computadora por cable USB.
     * 
     * @param packageName Paquete al que se otorgará el permiso (ej: nuestro com.rammonitor.app).
     * @param permissionName Nombre del permiso del sistema (ej: Manifest.permission.PACKAGE_USAGE_STATS).
     * @param userId Identificador del usuario Android (0 para el usuario principal del teléfono).
     */
    suspend fun grantRuntimePermission(
        packageName: String,
        permissionName: String,
        userId: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_state.value.isPrivileged) return@withContext false

        // Método 1: Invocación directa a través del Binder envuelto de IPackageManager
        try {
            val service = SystemServiceHelper.getSystemService("package")
            if (service != null) {
                val wrappedBinder = ShizukuBinderWrapper(service)
                val iPackageManagerStub = Class.forName("android.content.pm.IPackageManager\$Stub")
                val asInterfaceMethod = iPackageManagerStub.getMethod("asInterface", IBinder::class.java)
                val packageManagerInstance = asInterfaceMethod.invoke(null, wrappedBinder)

                val grantMethod = packageManagerInstance.javaClass.getMethod(
                    "grantRuntimePermission",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                grantMethod.invoke(packageManagerInstance, packageName, permissionName, userId)
                return@withContext true
            }
        } catch (_: Exception) {
            // Si el método Binder directo varía según la versión OEM de Android, pasamos al método 2 (shell pm)
        }

        // Método 2: Ejecución privilegiada de 'pm grant' mediante comando Shell de Shizuku
        try {
            val command = "pm grant $packageName $permissionName"
            val exitCode = executePrivilegedCommand(command)
            return@withContext exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fuerza la detención ('force-stop') de una aplicación que esté consumiendo memoria excesiva.
     * Solo es posible con los privilegios ADB / Shell proporcionados por Shizuku.
     */
    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!_state.value.isPrivileged) return@withContext false
        val exitCode = executePrivilegedCommand("am force-stop $packageName")
        exitCode == 0
    }

    /**
     * Ejecuta una orden de shell dentro del entorno privilegiado de Shizuku de forma no bloqueante.
     * En la versión actual de la API de Shizuku (v13+), el método newProcess tiene visibilidad interna/privada;
     * por ello se invoca mediante reflexión dinámica para mantener la capacidad de ejecución privileged am force-stop.
     */
    private suspend fun executePrivilegedCommand(cmd: String): Int = withContext(Dispatchers.IO) {
        try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val method = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
            process.waitFor()
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Consulta todos los procesos y aplicaciones en ejecución en el sistema operativo mediante
     * privilegios de ADB/Root de Shizuku (ejecutando 'dumpsys meminfo' o 'ps -A').
     * Supera la restricción del sandbox de Android 8-15 que solo devuelve la app actual.
     */
    suspend fun fetchRunningProcessesViaShizuku(): List<ShizukuProcessDump> = withContext(Dispatchers.IO) {
        if (!_state.value.isPrivileged) return@withContext emptyList()
        val results = mutableListOf<ShizukuProcessDump>()
        try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val method = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            // Ejecutamos 'dumpsys meminfo' para obtener el PSS exacto de todas las apps
            val process = method.invoke(null, arrayOf("sh", "-c", "dumpsys meminfo"), null, null) as java.lang.Process

            val reader = process.inputStream.bufferedReader()
            var line: String?
            var inProcessSection = false
            // Coincide con líneas como: "    145,200K: com.whatsapp (pid 5610 / activities)"
            val regex = Regex("""^\s*([\d,]+)K:\s+([^\s]+)\s+\(pid\s+(\d+)""")

            while (reader.readLine().also { line = it } != null) {
                val curLine = line ?: break
                if (!inProcessSection) {
                    if (curLine.contains("Total PSS by process:")) {
                        inProcessSection = true
                    }
                    continue
                }

                // Fin de la sección de procesos para no procesar miles de líneas innecesarias
                if (curLine.contains("Total PSS by OOM") ||
                    curLine.contains("Total PSS by category") ||
                    curLine.contains("Total RAM:")
                ) {
                    break
                }

                val match = regex.find(curLine)
                if (match != null) {
                    val pssKbStr = match.groupValues[1].replace(",", "")
                    val pssKb = pssKbStr.toIntOrNull() ?: 0
                    val pkgName = match.groupValues[2]
                    val pid = match.groupValues[3].toIntOrNull() ?: 0

                    val isActivities = curLine.contains("/ activities")
                    val isForeground = isActivities || curLine.contains("top-activity")

                    results.add(
                        ShizukuProcessDump(
                            pid = pid,
                            packageName = pkgName,
                            pssKb = pssKb,
                            isForeground = isForeground
                        )
                    )
                }
            }
            process.destroy()
        } catch (_: Exception) {
            // Si dumpsys falla temporalmente, probamos con ps -A
        }

        // Fallback rápido con 'ps -A -o PID,NAME,RSS' si dumpsys vino vacío
        if (results.isEmpty()) {
            try {
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val method = shizukuClass.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", "ps -A -o PID,NAME,RSS"), null, null) as java.lang.Process
                val reader = process.inputStream.bufferedReader()
                var line: String?
                var isHeader = true
                while (reader.readLine().also { line = it } != null) {
                    val curLine = line?.trim() ?: break
                    if (isHeader) {
                        isHeader = false
                        continue
                    }
                    val parts = curLine.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        val pid = parts[0].toIntOrNull() ?: continue
                        val name = parts[1]
                        val rssKb = parts[2].toIntOrNull() ?: 0
                        if (rssKb > 0 && (name.contains(".") || !name.startsWith("["))) {
                            results.add(
                                ShizukuProcessDump(
                                    pid = pid,
                                    packageName = name,
                                    pssKb = rssKb,
                                    isForeground = false
                                )
                            )
                        }
                    }
                }
                process.destroy()
            } catch (_: Exception) {}
        }

        results
    }
}

/**
 * Representa una captura instantánea de proceso leída con privilegios Shizuku.
 */
data class ShizukuProcessDump(
    val pid: Int,
    val packageName: String,
    val pssKb: Int,
    val isForeground: Boolean = false
)
