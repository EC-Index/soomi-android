# SOOMI ProGuard Rules

# Keep Room entities
-keep class com.soomi.baby.data.local.entity.** { *; }

# Keep data classes for Gson
-keepclassmembers class com.soomi.baby.** {
    <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-dontwarn androidx.compose.**
