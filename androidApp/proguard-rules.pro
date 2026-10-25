# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers so Play can deobfuscate crashes with the mapping file in the bundle.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Room KMP instantiates the generated database through its constructor lookup
-keep class * extends androidx.room.RoomDatabase { <init>(...); }
-keep class * implements androidx.room.RoomDatabaseConstructor { *; }

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

# SQLCipher
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Tink references annotation-only classes that are not on the classpath
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
