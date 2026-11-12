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
}
