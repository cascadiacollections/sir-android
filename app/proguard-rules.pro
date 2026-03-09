# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================
# OkHttp 5.x Rules
# ============================================
# OkHttp 5 ships its own R8/ProGuard rules via META-INF.
# These suppress warnings for optional platform-specific TLS providers.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================
# Play Services Cast 22.x Rules
# ============================================
# New internal GMS classes referenced by Cast SDK but not present at compile time
-dontwarn com.google.android.gms.common.api.ApiMetadata
-dontwarn com.google.android.gms.common.api.ComplianceOptions$Builder
-dontwarn com.google.android.gms.common.api.ComplianceOptions
-dontwarn com.google.android.gms.common.wrappers.AttributionSourceWrapper

# ============================================
# Media3 / ExoPlayer Rules
# ============================================
# Keep only what we need for audio streaming
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.source.ProgressiveMediaSource { *; }
-keep class androidx.media3.datasource.okhttp.** { *; }
-dontwarn androidx.media3.**


# ============================================
# Kotlin Coroutines
# ============================================
-dontwarn kotlinx.coroutines.**

# ============================================
# Aggressive optimizations for release
# ============================================
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-mergeinterfacesaggressively
-overloadaggressively

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Strip out Kotlin metadata (saves ~50KB)
-dontwarn kotlin.Metadata
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
}

# Remove unused Compose debugging info
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    public static boolean isTraceInProgress();
    public static void traceEventStart(...);
    public static void traceEventEnd();
}

