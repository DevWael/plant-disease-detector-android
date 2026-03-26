# Kotlin/Compose defaults
-dontwarn kotlinx.serialization.**
-keep class com.bbioon.plantdisease.data.model.** { *; }

# Tink / Security-Crypto (errorprone annotations are compile-only)
-dontwarn com.google.errorprone.annotations.**
