# Reglas de ofuscación y optimización ProGuard
-keepattributes *Annotation*
-dontwarn java.lang.invoke.**
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
