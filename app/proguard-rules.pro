# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# JGit
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.** { *; }

# JSON
-keep class org.json.** { *; }

# SLF4J
-dontwarn org.slf4j.**

# Keep data classes used for serialization
-keep class com.jack.pushgithub.data.** { *; }
-keep class com.jack.pushgithub.github.FileInfo { *; }

# Keep annotations
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod