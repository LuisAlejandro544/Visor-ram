# 📁 Estructura del Proyecto (STRUCTURE)

Este documento detalla la organización de carpetas, responsabilidades de cada módulo y el flujo de datos dentro de **RAM Monitor Pro**.

---

## 🌳 Árbol General del Repositorio

```text
├── .github/
│   └── workflows/
│       └── build-debug-apk.yml     # GitHub Action para compilación manual de APK Debug
├── android/                        # Proyecto nativo Android (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── build.gradle.kts        # Dependencias (Compose, Shizuku, Coroutines), ABIs y SDKs
│   │   ├── proguard-rules.pro      # Reglas ProGuard de optimización y ofuscación
│   │   └── src/main/
│   │       ├── AndroidManifest.xml # Permisos, servicio en primer plano y ShizukuProvider
│   │       ├── res/                # Recursos XML (strings, colors, themes, drawables)
│   │       └── java/com/rammonitor/app/
│   │           ├── MainActivity.kt                 # Actividad principal y control de navegación
│   │           ├── data/
│   │           │   ├── model/
│   │           │   │   ├── MemoryStats.kt          # Modelo inmutable de métricas del kernel
│   │           │   │   └── ProcessInfo.kt          # Modelo inmutable de proceso individual
│   │           │   ├── monitor/
│   │           │   │   └── MemoryReader.kt         # Lector asíncrono (/proc/meminfo + PSS)
│   │           │   └── shizuku/
│   │           │       └── ShizukuManager.kt       # Gestor Binder, permisos y force-stop
│   │           ├── service/
│   │           │   └── RamMonitorService.kt        # Foreground Service de bajo impacto
│   │           └── ui/
│   │               ├── navigation/
│   │               │   └── Screen.kt               # Definición de rutas y destinos
│   │               ├── screens/
│   │               │   ├── ShizukuGateScreen.kt    # Pantalla de bloqueo y guía sin PC
│   │               │   ├── DashboardScreen.kt      # Medidor de RAM, ZRAM y métricas
│   │               │   ├── ProcessListScreen.kt    # Lista, búsqueda y cierre de apps
│   │               │   └── SettingsScreen.kt       # Frecuencias, alertas y ABIs del CPU
│   │               └── theme/
│   │                   ├── Color.kt                # Paleta técnica de alto contraste
│   │                   └── Theme.kt                # Configuración de Material 3
│   ├── gradle/wrapper/             # Distribución del Gradle Wrapper (8.9)
│   ├── build.gradle.kts            # Plugins globales (AGP 8.7.2, Kotlin 2.0.21)
│   ├── gradle.properties           # Ajustes de memoria JVM y AndroidX
│   ├── gradlew                     # Script de ejecución para Linux/macOS/Termux
│   ├── gradlew.bat                 # Script de ejecución para Windows
│   └── settings.gradle.kts         # Repositorios (Google, MavenCentral) y módulos
├── src/                            # Entorno web ligero (Hello World de compilación Vite)
│   ├── App.tsx
│   ├── index.css
│   └── main.tsx
├── AGENTS.md                       # Normas y directrices para agentes autónomos
├── AI_CONTEXT.md                   # Contexto técnico y restricciones de negocio para IAs
├── README.md                       # Presentación general del proyecto
├── README_AI.md                    # Guía para modelos de lenguaje que colaboren en el repo
├── ROADMAP.md                      # Fases y funcionalidades planificadas
└── STRUCTURE.md                    # Este archivo
```

---

## 🧩 Responsabilidades de los Paquetes en Android

### 1. Capa de Datos (`com.rammonitor.app.data`)
* **`model/MemoryStats.kt`**:
  * Estructura inmutable que almacena la fotografía instantánea de la memoria física y ZRAM.
  * Incluye métodos de conversión (`totalInGb()`, `usedInGb()`, `availableInGb()`, `swapUsedInMb()`).
* **`model/ProcessInfo.kt`**:
  * Representa un proceso en ejecución: PID, nombre de app, nombre de paquete, PSS en KB, porcentaje sobre el total de RAM, clasificación (*Sistema* o *Usuario*) y nivel de importancia en Android.
* **`monitor/MemoryReader.kt`**:
  * Ejecuta la lectura en el hilo de fondo con `withContext(Dispatchers.IO)`.
  * Lee el archivo virtual `/proc/meminfo` del kernel y utiliza `ActivityManager.getProcessMemoryInfo` en lote para minimizar llamadas IPC.
* **`shizuku/ShizukuManager.kt`**:
  * Administra la conexión con el servicio Shizuku/Sui.
  * Implementa la lógica para otorgar permisos de sistema mediante el Binder de `IPackageManager` envuelto en `ShizukuBinderWrapper`.
  * Ejecuta comandos privilegiados como `am force-stop` para detener procesos acaparadores de memoria.

### 2. Capa de Servicios (`com.rammonitor.app.service`)
* **`service/RamMonitorService.kt`**:
  * Se registra como `foregroundServiceType="dataSync"`.
  * Publica el estado global a través de flujos `StateFlow<MemoryStats>` y `StateFlow<List<ProcessInfo>>`.
  * Actualiza la notificación del sistema con prioridad baja (`IMPORTANCE_LOW`) para mantener informado al usuario sin gastar batería ni sonar.

### 3. Capa de Interfaz de Usuario (`com.rammonitor.app.ui`)
* **`screens/ShizukuGateScreen.kt`**:
  * Puerta de enlace obligatoria: si Shizuku no está listo, guía al usuario a activarlo vía Depuración Inalámbrica.
* **`screens/DashboardScreen.kt`**:
  * Medidor circular de consumo animado con semáforo dinámico (Verde < 65%, Amarillo 65-85%, Rojo > 85%).
  * Tarjetas de desglose técnico y botón de optimización rápida.
* **`screens/ProcessListScreen.kt`**:
  * Lista reactiva con buscador en vivo, chips de filtrado (*Usuario*, *Sistema*, *>100MB*) y modal de inspección con opción de forzar cierre mediante Shizuku.
* **`screens/SettingsScreen.kt`**:
  * Frecuencia de muestreo (1s a 10s), umbral de alerta de memoria, visor de arquitecturas ABI del procesador y estado del enlace Shizuku.
