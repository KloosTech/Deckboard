# kotlinx.serialization: keep generated serializers of the API models.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class de.kloostech.deckboard.** {
    *** Companion;
}
-keepclasseswithmembers class de.kloostech.deckboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.kloostech.deckboard.**$$serializer { *; }

# Ktor logs through SLF4J; the API is optional on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
