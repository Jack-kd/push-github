# Room
-keepnames class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }
-keepattributes *Annotation*
-dontwarn com.google.android.material.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep public class * implements androidx.work.WorkerFactory

# OkHttp - 仅保留必要的公共 API
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.Request { *; }
-keep class okhttp3.Response { *; }
-keep class okhttp3.Headers { *; }
-keep class okhttp3.MediaType { *; }
-keep class okhttp3.RequestBody { *; }
-keep class okhttp3.ResponseBody { *; }
-keep class okhttp3.Call { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# JGit
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.api.Git { *; }
-keep class org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider { *; }
-keep class org.eclipse.jgit.transport.RemoteRefUpdate { *; }
-keep class org.eclipse.jgit.transport.RemoteRefUpdate$Status { *; }
-keep class org.eclipse.jgit.lib.ProgressMonitor { *; }
-keep class org.eclipse.jgit.api.ResetCommand { *; }
-keep class org.eclipse.jgit.api.ResetCommand$ResetType { *; }

# JSON
-keep class org.json.** { *; }

# JGit SSH / Tink dependencies - classes not available on Android
-dontwarn org.slf4j.**
-dontwarn org.apache.sshd.**
-dontwarn org.bouncycastle.**
-dontwarn javax.management.**
-dontwarn javax.security.auth.login.**
-dontwarn com.google.errorprone.annotations.**

# 忽略 R8 缺失类警告，允许构建继续
-ignorewarnings