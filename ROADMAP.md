# 🗺️ Hoja de Ruta del Proyecto (ROADMAP)

Este documento describe la visión, fases de desarrollo e hitos futuros para **RAM Monitor Pro**.

---

## 📌 Fase 1: Arquitectura Base y Puerta de Enlace Shizuku (Completada ✅)
- [x] Estructura inicial del proyecto Android con Kotlin y Gradle KTS.
- [x] Configuración de `minSdk 26` (Android 8.0 Oreo) y `targetSdk 35` (Android 15).
- [x] Configuración de filtros ABI para arquitecturas de 32 bits y 64 bits (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
- [x] Lector de bajo nivel de memoria en Linux kernel (`/proc/meminfo` + `ActivityManager.getProcessMemoryInfo`).
- [x] `RamMonitorService`: Foreground Service de bajo impacto con notificación persistente reactiva.
- [x] Integración de la API oficial de **Shizuku (v13.1.5)** y `ShizukuProvider`.
- [x] `ShizukuGateScreen`: Bloqueo de la app hasta que Shizuku esté activo y autorizado, con guía sin PC mediante Depuración inalámbrica.
- [x] Auto-concesión de permisos de sistema (`PACKAGE_USAGE_STATS`) vía Binder de `IPackageManager`.
- [x] Cierre forzado (*force-stop*) de procesos agresivos mediante comandos de ADB en Shizuku.
- [x] Interfaz modular en Jetpack Compose (Dashboard, Lista de Procesos y Ajustes).
- [x] Workflow de GitHub Actions manual sin caché (`workflow_dispatch`) para compilar APK Debug.

---

## 🚀 Fase 2: Desglose Avanzado de Memoria y Telemetría Gráfica (En Progreso ⏳)
- [x] **Registro y Telemetría de Picos de Consumo**: Medición en vivo del pico máximo global de RAM (%), valor en GB, hora exacta del pico, fluctuación mínima/máxima y mayor pico individual por aplicación con opción de reinicio de sesión.
- [ ] **Historial Gráfico en Tiempo Real**: Gráfica de línea temporal (últimos 60 segundos y últimos 10 minutos) que visualice picos de consumo de RAM sin sobrecargar la CPU.
- [ ] **Desglose de Memoria PSS Detallado**:
  - `Private Dirty RAM` (Memoria propia y modificada por la app).
  - `Shared Dirty RAM` (Memoria compartida con otras librerías y el sistema).
  - `Native Heap` vs `Dalvik/ART Heap`.
- [ ] **Análisis de ZRAM y Algoritmo de Compresión**: Lectura del ratio de compresión en `/sys/block/zram0/` (ej: LZ4, ZSTD) para medir cuánta RAM física real se está ahorrando.
- [ ] **Filtro por Categorías**: Separar procesos en Juegos, Redes Sociales, Mensajería, Navegadores y Servicios de Fondo.

---

## ⚡ Fase 3: Automatizaciones y Alertas Inteligentes (Futura 📅)
- [ ] **Alertas Flotantes de Pico de Memoria**: Notificación emergente configurable cuando una aplicación en segundo plano consuma más del X% de RAM asignada.
- [ ] **Perfil de Juego / Alto Rendimiento (Game Boost Limpio)**:
  - Liberación previa de apps suspendidas antes de iniciar una app pesada o juego.
  - *Regla estricta*: **Nunca** utilizar propiedades riesgosas `persist.sys.*`.
- [ ] **Modo Noche / Ahorro Extremo**: Pausar lecturas automáticamente cuando la pantalla esté apagada y reanudarlas al encenderse (`ScreenReceiver`).
- [ ] **Exportación de Registros de Consumo**: Guardar reportes en CSV o texto plano para auditar qué apps drenan la memoria a lo largo del día.

---

## 📦 Fase 4: Distribución y Empaquetado Final (Lanzamiento 🚀)
- [ ] Firma de APK para distribución pública fuera de Google Play (Uptodown, F-Droid, APKPure, GitHub Releases).
- [ ] Workflow de GitHub Actions para generar compilaciones Release firmadas con KeyStore almacenada en Secrets.
- [ ] Traducción multi-idioma (Español e Inglés).
- [ ] Soporte para Widgets en la pantalla de inicio con el porcentaje de RAM libre.
