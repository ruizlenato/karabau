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

# Google Tink (used by EncryptedSharedPreferences) references errorprone
# annotations at compile time only — safe to suppress for R8.
-dontwarn com.google.errorprone.annotations.**

# Keep API model classes — Gson uses reflection to serialize/deserialize
# field names, which R8 would otherwise rename or strip.
-keep class com.ruizlenato.karabau.data.model.** { *; }
-keep class com.ruizlenato.karabau.data.remote.TrpcInput { *; }
-keep class com.ruizlenato.karabau.data.remote.TrpcResponse { *; }
-keep class com.ruizlenato.karabau.data.remote.TrpcResult { *; }
-keep class com.ruizlenato.karabau.data.remote.TrpcResultData { *; }
-keep class com.ruizlenato.karabau.data.remote.TrpcError { *; }