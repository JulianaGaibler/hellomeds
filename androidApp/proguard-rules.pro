# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Open-source app: keep source names in stack traces (Play Console pre-review reports,
# user-submitted logs, third-party crash tools). R8 still shrinks and optimizes.
-dontobfuscate

# Keep ML Kit classes (on-device object detection and text recognition)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep ML Kit GenAI classes (on-device generative AI with Gemini Nano)
-keep class com.google.mlkit.genai.** { *; }
-dontwarn com.google.mlkit.genai.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Room KMP — entities, DAOs, and database
-keep class me.juliana.hellomeds.data.database.** { *; }
-keep class me.juliana.hellomeds.data.dao.** { *; }

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# kotlinx.serialization (Navigation 3 routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlinx.datetime
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**

# SQLCipher
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Tink (used by EncryptedSharedPreferences for database key storage)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
