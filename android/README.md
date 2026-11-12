# RAM Monitor Pro - Proyecto Android en Kotlin con Shizuku

Monitor de memoria RAM física, ZRAM (Swap) y gestor avanzado de procesos en segundo plano con privilegios **ADB (UID 2000)** y **Root (UID 0)** mediante la API oficial de **Shizuku (v13.1.5)**.

---

## ⚡ ¿Por qué es obligatorio Shizuku en esta App?

En las versiones modernas de Android (Android 8 hasta Android 15), el sistema operativo restringe fuertemente la seguridad:
1. **Aislamiento de procesos (Sandbox)**: Ninguna app estándar puede consultar la memoria RAM (PSS) exacta de otras aplicaciones.
2. **Restricción de terminación**: Las apps normales solo pueden cerrar sus propios hilos o pedirle al sistema que cierre cachés viejos (`killBackgroundProcesses`). Con Shizuku, nuestra app obtiene privilegios de ADB Shell y puede ejecutar `am force-stop` directamente para liberar memoria real de apps agresivas.
3. **Concesión autónoma de permisos**: Con Shizuku, la app puede auto-otorgarse permisos de sistema protegidos (`PACKAGE_USAGE_STATS`) sin requerir conectar el móvil a una computadora.

---

## 🔍 Explicación del Código de IPackageManager

El usuario proporcionó el siguiente código de referencia:

```java
private static final IPackageManager PACKAGE_MANAGER = IPackageManager.Stub.asInterface(
    new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")));

public static void grantRuntimePermission(String packageName, String permissionName, int userId) {
    try {
        PACKAGE_MANAGER.grantRuntimePermission(packageName, permissionName, userId);
    } catch (RemoteException tr) {
        throw new RuntimeException(tr.getMessage(), tr);
    }
}
```

### ¿Para qué sirve exactamente este código?
1. **`SystemServiceHelper.getSystemService("package")`**: Obtiene el identificador `IBinder` del servicio interno de Android encargado de gestionar todas las aplicaciones instaladas (`PackageManagerService`).
2. **`new ShizukuBinderWrapper(...)`**: Envuelve ese Binder para que cualquier petición que pase por él sea ejecutada con la identidad y credenciales del servicio Shizuku (UID 2000 / Shell de ADB o UID 0 / Root) en lugar de los permisos limitados de una app normal.
3. **`IPackageManager.Stub.asInterface(...)`**: Convierte el Binder en la interfaz IPC interna de Android (`IPackageManager`).
4. **`grantRuntimePermission(packageName, permissionName, userId)`**: Ejecuta la llamada que otorga un permiso del sistema en tiempo de ejecución.
   * **Beneficio directo**: Permite que nuestra app se auto-conceda permisos como `android.permission.PACKAGE_USAGE_STATS` (Estadísticas de apps) o permisos de depuración sin que el usuario tenga que conectar un cable USB a una PC para escribir comandos en la consola `adb shell`.
   * En nuestra app, esta lógica está implementada de forma modular en **`ShizukuManager.grantRuntimePermission(...)`**.

---

## 📱 Cómo Iniciar Shizuku en el Teléfono SIN PC

Como el usuario no tiene PC, puede iniciar Shizuku directamente desde el teléfono (Android 11 o superior):

1. **Conéctate a una red Wi-Fi** (o enciende tu propia zona Wi-Fi/Hotspot portátil).
2. **Activa Opciones de Desarrollador**:
   * Ajustes > Información del teléfono > Toca 7 veces seguidas sobre "Número de compilación".
3. **Activa la Depuración Inalámbrica**:
   * Ajustes > Opciones de Desarrollador > Activa **Depuración inalámbrica** (Wireless Debugging).
4. **Vincula con Shizuku**:
   * Entra a la app oficial de Shizuku y presiona "Vincular" (Pairing).
   * Ingresa a "Vincular dispositivo con código de vinculación" en los ajustes de depuración inalámbrica.
   * Introduce el código de 6 dígitos que aparecerá en la notificación.
5. **Inicia Shizuku**:
   * Presiona "Iniciar" (Start) dentro de Shizuku.
   * Abre **RAM Monitor Pro**: la app detectará automáticamente el servicio y se desbloquearán todas las herramientas.

---

## 🛠️ Estructura del Proyecto

```text
android/
├── build.gradle.kts                # Configuración raíz de Gradle y plugins
├── settings.gradle.kts             # Repositorios MavenCentral y Google
├── gradle.properties               # Optimizaciones JVM y AndroidX
├── gradlew                         # Script ejecutable de Gradle
├── app/
│   ├── build.gradle.kts            # Dependencias: Compose BOM, Shizuku 13.1.5, ABI Filters
│   ├── proguard-rules.pro          # Reglas de ofuscación
│   └── src/main/
│       ├── AndroidManifest.xml     # Declaración de permisos y ShizukuProvider
│       ├── res/                    # Iconos vectoriales y temas
│       └── java/com/rammonitor/app/
│           ├── MainActivity.kt                 # Punto de entrada, control de acceso y NavHost
│           ├── data/
│           │   ├── model/
│           │   │   ├── MemoryStats.kt          # Métricas globales del kernel
│           │   │   └── ProcessInfo.kt          # Consumo de memoria por app
│           │   ├── monitor/
│           │   │   └── MemoryReader.kt         # Lectura de /proc/meminfo y PSS
│           │   └── shizuku/
│           │       └── ShizukuManager.kt       # Gestor Binder, force-stop y concesión de permisos
│           ├── service/
│           │   └── RamMonitorService.kt        # Foreground service de bajo impacto
│           └── ui/
│               ├── navigation/Screen.kt        # Rutas de navegación
│               ├── screens/
│               │   ├── ShizukuGateScreen.kt    # Pantalla de bloqueo/guía de conexión
│               │   ├── DashboardScreen.kt      # Medidor de RAM y métricas del kernel
│               │   ├── ProcessListScreen.kt    # Lista y cierre forzoso (Shizuku)
│               │   └── SettingsScreen.kt       # Ajustes, ABIs y estado Shizuku
│               └── theme/
│                   ├── Color.kt                # Paleta Slate/Cyberpunk oscura
│                   └── Theme.kt                # Tema Material 3
```

---

## 🚀 Compilación del APK

### En el propio móvil (Termux):
```bash
cd android
chmod +x gradlew
./gradlew assembleDebug
```
El archivo generado estará disponible en:
```text
android/app/build/outputs/apk/debug/app-debug.apk
```
Listo para instalar en cualquier dispositivo Android (arm64-v8a, armeabi-v7a, x86_64).
