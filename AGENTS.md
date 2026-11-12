# 🤖 Guía para Agentes de Desarrollo (AGENTS)

Este documento contiene las instrucciones operativas, directrices de arquitectura y patrones de código que cualquier agente de software automatizado debe seguir al realizar modificaciones o añadir funciones a **RAM Monitor Pro**.

---

## 🧭 Principios Operativos del Agente

1. **Razonar antes de Actuar**:
   * No generes código de forma precipitada.
   * Analiza el impacto de los cambios propuestos en la estabilidad del servicio en segundo plano y el consumo de batería del teléfono.
   * Respeta la arquitectura modular existente: nunca mezcles la lógica de lectura de memoria con la capa de presentación Compose.

2. **No romper la lógica preexistente**:
   * Los cambios deben ser quirúrgicos y aditivos. No refactorices componentes funcionales existentes sin una necesidad técnica explícita.
   * No cambies el diseño visual ni los iconos a menos que el usuario lo solicite expresamente.

3. **Explicación Detallada en Código**:
   * Todo nuevo archivo Kotlin debe incluir un encabezado KDoc explicando claramente **para qué sirve la clase o módulo**, su ciclo de vida y cómo interactúa con el resto del sistema.
   * Añade comentarios breves en las funciones donde la lógica del kernel de Linux o las llamadas IPC de Shizuku no sean inmediatamente evidentes.

---

## 🛠️ Patrones de Implementación Obligatorios

### Concurrencia y Corrutinas
```kotlin
// ✅ BIEN: Operación asíncrona encapsulada en Dispatchers.IO
suspend fun fetchMemoryData(): MemoryStats = withContext(Dispatchers.IO) {
    // Lectura de /proc/meminfo o IPC de Shizuku
}

// ❌ MAL: Bloquear el hilo de interfaz o ejecutar llamadas pesadas en el Main thread
fun badFetch(): MemoryStats {
    val file = File("/proc/meminfo").readText() // Provoca tirones (ANR)
}
```

### Integración con Shizuku
```kotlin
// ✅ BIEN: Verificar el estado del Binder antes de invocar operaciones privilegiadas
if (ShizukuManager.state.value.isPrivileged) {
    ShizukuManager.forceStopPackage(packageName)
} else {
    // Guiar al usuario a ShizukuGateScreen
}
```

### Separación de Pantallas y Estados
* Todo estado reactivo consumido en Jetpack Compose debe utilizar `collectAsStateWithLifecycle()` para respetar el ciclo de vida de la actividad y detener el consumo de CPU cuando la app esté en segundo plano o minimizada.
* La navegación debe gestionarse a través de `NavController` utilizando los destinos definidos en `Screen.kt`.

---

## 📋 Checklist de Verificación para Agentes

Antes de dar por completada una tarea, el agente debe verificar:
- [ ] ¿El código nuevo compila limpiamente sin advertencias críticas ni errores de importación?
- [ ] ¿Las operaciones de E/S están delegadas a `Dispatchers.IO`?
- [ ] ¿Los nuevos permisos están justificados y declarados en `AndroidManifest.xml`?
- [ ] ¿Se respeta la prohibición estricta de **`persist.sys.*`**?
- [ ] ¿Las explicaciones y comentarios en el código están en **español**?
- [ ] ¿El entorno web starter sigue respondiendo con su *Hello World* sin errores de compilación ni de linting?
