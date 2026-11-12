# 🧠 Contexto para Modelos de Inteligencia Artificial (AI_CONTEXT)

Este archivo define el contexto de negocio, restricciones técnicas obligatorias y el perfil del usuario para cualquier modelo de Inteligencia Artificial que colabore en este proyecto.

---

## 👤 Perfil del Usuario y Entorno de Trabajo

1. **Dispositivo del Desarrollador**:
   * El usuario **NO dispone de una computadora (PC)**. Todo su trabajo y pruebas se realizan directamente desde un **teléfono móvil Android**.
   * Las compilaciones se gestionan mediante **GitHub Actions** manuales o terminales móviles como **Termux**.
   * Las descargas de APK se realizan directamente desde la sección de artefactos de GitHub hacia el teléfono.

2. **Canal de Distribución**:
   * La app **NO se subirá a Google Play Store**. Se distribuirá a través de **Uptodown**, repositorios independientes o instalación directa de APK.
   * Por lo tanto, no se deben imponer restricciones arbitrarias de Google Play (como prohibir Shizuku, bloquear el uso de permisos especiales o limitar APIs avanzadas).

3. **Preferencias de Diseño**:
   * Al usuario **no le gusta el minimalismo extremo**. Prefiere interfaces técnicas, densas en información útil, estilo instrumentación de hardware, con gráficas, métricas legibles, semáforos de advertencia y detalles técnicos visibles.
   * La navegación debe ser **modular y separada en diferentes pantallas** (`Dashboard`, `Procesos`, `Ajustes`). Nunca se debe aglomerar todo en una única pantalla desordenada.

---

## 🚫 Reglas Técnicas Estrictas y Prohibiciones

1. **PROHIBIDO usar `persist.sys.*`**:
   * Bajo ninguna circunstancia se debe sugerir, programar o alterar propiedades del sistema que comiencen con `persist.sys.*` (típicas de tweaks peligrosos de "game boosters"). Modificar estas propiedades puede ocasionar *bootloops* (bucles de reinicio) en dispositivos Android.

2. **Shizuku es OBLIGATORIO**:
   * Esta aplicación exige la presencia y autorización de **Shizuku** (o Sui) para operar.
   * No se deben añadir soluciones ficticias o fallbacks que intenten simular permisos de sistema sin Shizuku. La pantalla `ShizukuGateScreen` debe proteger el acceso a las funciones principales.

3. **Restricción de Licencias de Dependencias**:
   * No utilizar dependencias con licencias copyleft agresivas (**GPL**, **AGPL**) que obliguen a publicar el código bajo términos restrictivos.
   * Preferir siempre librerías bajo licencias permisivas como **Apache 2.0** o **MIT** (las librerías oficiales de AndroidX, Jetpack Compose y Shizuku son Apache 2.0).

4. **Uso de Dependencias Reales**:
   * El usuario prefiere soluciones robustas apoyadas en librerías probadas y dependencias funcionales, en lugar de intentar reinventar la rueda con código artesanal sin librerías. El peso final del APK no es una preocupación crítica.

5. **Manejo Estricto de Hilos y Corrutinas**:
   * **Nunca** ejecutar operaciones de lectura de archivos (`/proc/meminfo`), llamadas IPC de Shizuku ni procesamiento de procesos en el hilo principal (`Dispatchers.Main`).
   * Toda tarea pesada debe estar encapsulada en `withContext(Dispatchers.IO)` o `Dispatchers.Default`.

6. **Arquitecturas de Hardware (32 y 64 bits)**:
   * Mantener siempre configurados los filtros ABI en Gradle: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.

7. **Compatibilidad de Versiones Android**:
   * La versión mínima admitida es **`minSdk = 26`** (Android 8.0 Oreo), que ofrece balance entre compatibilidad con dispositivos antiguos y soporte nativo para `NotificationChannel` y servicios en primer plano.
   * No elevar `minSdk` salvo que una función justificada lo exija y el usuario lo apruebe.

8. **Idioma del Proyecto**:
   * Todo comentario explicativo en el código, documentación y mensajes debe estar redactado en **Español**.
   * Si existe un archivo `commit_message.txt`, debe mantenerse en español y no ser modificado salvo que el usuario lo solicite expresamente.
