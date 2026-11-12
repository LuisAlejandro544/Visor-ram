# 🤖 README para Inteligencias Artificiales (README_AI)

Documento condensado de lectura rápida diseñado para que modelos de lenguaje (LLMs) y agentes autónomos comprendan instantáneamente el propósito, la arquitectura y las reglas de este repositorio.

---

## ⚡ Resumen Ejecutivo del Proyecto

* **Nombre**: RAM Monitor Pro
* **Plataforma**: Android Nativo (Kotlin 2.0.21 + Jetpack Compose + AGP 8.7.2)
* **Objetivo**: Monitoreo en segundo plano de RAM física y ZRAM Swap con mínimo impacto en batería, y administración de procesos mediante privilegios avanzados de Shizuku.
* **Componente Crítico**: **Shizuku API (v13.1.5)**. La aplicación exige Shizuku para ejecutarse; si no está activo, la pantalla `ShizukuGateScreen` bloquea el acceso y asiste al usuario en la conexión mediante Depuración Inalámbrica.
* **Entorno del Usuario**: Desarrollador móvil **sin PC**. Compila y prueba mediante GitHub Actions manuales o Termux en el teléfono.

---

## 🗺️ Mapa de Archivos Clave

| Ruta | Descripción |
| :--- | :--- |
| `android/app/src/main/java/com/rammonitor/app/MainActivity.kt` | Punto de entrada, escucha de permisos, verificación de Shizuku y navegación Compose. |
| `android/app/src/main/java/com/rammonitor/app/data/shizuku/ShizukuManager.kt` | Gestor del Binder de Shizuku, auto-concesión de `PACKAGE_USAGE_STATS` vía `IPackageManager` y `am force-stop`. |
| `android/app/src/main/java/com/rammonitor/app/data/monitor/MemoryReader.kt` | Lector asíncrono (`Dispatchers.IO`) de `/proc/meminfo` y PSS por proceso. |
| `android/app/src/main/java/com/rammonitor/app/service/RamMonitorService.kt` | Foreground Service con notificación continua de bajo consumo (`IMPORTANCE_LOW`). |
| `android/app/src/main/java/com/rammonitor/app/ui/screens/ShizukuGateScreen.kt` | Pantalla de bienvenida / bloqueo con tutorial de depuración inalámbrica sin PC. |
| `android/app/src/main/java/com/rammonitor/app/ui/screens/DashboardScreen.kt` | Indicador circular de RAM, tarjetas de métricas del kernel y acciones rápidas. |
| `android/app/src/main/java/com/rammonitor/app/ui/screens/ProcessListScreen.kt` | Lista de apps en memoria con búsqueda, filtros y forzado de detención. |
| `android/app/src/main/java/com/rammonitor/app/ui/screens/SettingsScreen.kt` | Frecuencias de muestreo (1s-10s), umbrales de alerta y visor de ABIs del CPU. |
| `.github/workflows/build-debug-apk.yml` | Workflow de compilación manual sin caché (`workflow_dispatch`). |

---

## ⚠️ Reglas Inquebrantables de Código

1. **PROHIBIDO `persist.sys.*`**: No tocar bajo ningún concepto variables de sistema que inicien con este prefijo.
2. **Concurrencia Segura**: Ninguna operación de E/S o llamada IPC en el hilo principal (`Dispatchers.Main`). Utilizar `withContext(Dispatchers.IO)`.
3. **Licencias Permitidas**: Solo dependencias con licencia permisiva (Apache 2.0 / MIT). Prohibidas licencias copyleft (GPL/AGPL).
4. **Arquitecturas**: Soportar siempre 32 y 64 bits (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
5. **Idioma**: Todo comentario, documentación y mensaje en **Español**.
