# === OkHttp ===
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# === Kotlin Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# === AndroidX WorkManager ===
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class cn.qzxy.hotwater.HotWaterWidgetActionWorker { *; }

# === App Widget Provider ===
-keep class cn.qzxy.hotwater.HotWaterWidgetProvider { *; }

# === Data classes (JSON field names must survive) ===
-keep class cn.qzxy.hotwater.HotWaterConfig { *; }
-keep class cn.qzxy.hotwater.HotWaterConfig$Companion { *; }
-keep class cn.qzxy.hotwater.DashboardSnapshot { *; }
-keep class cn.qzxy.hotwater.OperationResult { *; }
-keep class cn.qzxy.hotwater.RuntimeDeviceInfo { *; }
-keep class cn.qzxy.hotwater.HotWaterConfigStore$WidgetState { *; }

# === ViewBinding ===
-keep class cn.qzxy.hotwater.databinding.** { *; }

# === AndroidX Security / EncryptedSharedPreferences ===
-keep class androidx.security.crypto.** { *; }

# === General Android ===
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
