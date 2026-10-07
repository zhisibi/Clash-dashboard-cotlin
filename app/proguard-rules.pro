-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class net.zash.clashpanel.**$$serializer { *; }
-keepclassmembers class net.zash.clashpanel.** { *** Companion; }
-keepclasseswithmembers class net.zash.clashpanel.** { kotlinx.serialization.KSerializer serializer(...); }

# readable crash logs
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
