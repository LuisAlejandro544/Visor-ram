# 🚀 RAM Monitor Pro

**RAM Monitor Pro** es una aplicación móvil nativa de alto rendimiento desarrollada en **Kotlin** y **Jetpack Compose**, diseñada para monitorizar en tiempo real el consumo de memoria RAM física, ZRAM (Swap) y los procesos en segundo plano de Android.

Para superar las estrictas restricciones de aislamiento (*sandbox*) de las versiones modernas de Android (Android 8 a Android 15), la aplicación aprovecha la API oficial de **Shizuku (v13.1.5)** con privilegios de **ADB Shell (UID 2000)** o **Root (UID 0)**.

---

## ✨ Características Principales

* 📊 **Monitoreo en Vivo de Memoria**: Lectura directa de `/proc/meminfo` del kernel de Linux (Memoria total, usada, libre, disponible, buffers, caché y ZRAM Swap comprimida).
* 🛡️ **Integración Obligatoria con Shizuku**:
  * Acceso a métricas de memoria PSS (*Proportional Set Size*) de todas las apps del sistema.
  * Auto-concesión de permisos de sistema (`android.permission.PACKAGE_USAGE_STATS`) en tiempo de ejecución invocando directamente el Binder de `IPackageManager`.
  * Forzar cierre (*force-stop*) de procesos agresivos que consumen RAM excesiva.
* 📱 **Desarrollo Pensado para Móvil (Sin PC)**:
  * Guía paso a paso integrada para emparejar e iniciar Shizuku mediante **Depuración inalámbrica** en Android 11+.
  * Workflow de **GitHub Actions manual** para compilar el archivo `.apk` en la nube y descargarlo directamente al teléfono.
* 🔋 **Servicio en Segundo Plano de Mínimo Consumo**:
  * `RamMonitorService` ejecuta un *Foreground Service* con notificación continua de baja prioridad (`IMPORTANCE_LOW`).
  * Frecuencia de muestreo configurable (1s, 3s, 5s o 10s).
  * Concurrencia gestionada con **Kotlin Coroutines** en `Dispatchers.IO` y `Dispatchers.Default` (cero impacto en el hilo principal de la UI).
* 🎨 **Interfaz Modular y No Minimalista**:
  * Construida con **Jetpack Compose** y **Material 3**.
  * Tema oscuro estilo instrumentación técnica de hardware (*Dark Slate & Cyan/Blue*).
  * Navegación desacoplada entre **Dashboard**, **Lista de Procesos** y **Ajustes**.
* ⚙️ **Compatibilidad Total**:
  * `minSdk 26` (Android 8.0 Oreo) / `targetSdk 35` (Android 15).
  * Arquitecturas compatibles de 32 bits y 64 bits (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
  * Sin dependencias de Google Play Services: listo para distribución en tiendas alternativas (Uptodown, F-Droid o APK directo).

---

## 🛠️ Compilación del APK

### Opción 1: Compilación Automática en GitHub (Desde el Teléfono)
El repositorio cuenta con una GitHub Action configurada para ejecutarse **exclusivamente de forma manual y sin caché**:
1. Abre este repositorio en la app de GitHub o en el navegador de tu teléfono.
2. Ve a la pestaña **Actions**.
3. Selecciona el flujo **"Compilar APK Debug (Manual)"**.
4. Pulsa en **"Run workflow"**.
5. Al finalizar (1-2 minutos), entra en la ejecución y descarga el archivo comprimido con el APK listo para instalar en tu teléfono.

### Opción 2: Compilación Local en el Móvil (Termux)
Si tienes Termux con OpenJDK 17 instalado:
```bash
cd android
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon --no-build-cache
```
El APK resultante se genera en:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Enlaces Rápidos a la Documentación

* [Estructura del Proyecto (STRUCTURE.md)](./STRUCTURE.md)
* [Hoja de Ruta y Próximas Fases (ROADMAP.md)](./ROADMAP.md)
* [Contexto Técnico para IAs (AI_CONTEXT.md)](./AI_CONTEXT.md)
* [Guía de Agentes y Buenas Prácticas (AGENTS.md)](./AGENTS.md)
* [README para Modelos de Lenguaje (README_AI.md)](./README_AI.md)

---

## 📜 Licencias
Todas las dependencias utilizadas (AndroidX, Jetpack Compose, KotlinX Coroutines, Material Icons, Shizuku API) cuentan con licencias abiertas permisivas tipo **Apache 2.0**, sin licencias copyleft (GPL/AGPL) que impongan restricciones indeseadas.
