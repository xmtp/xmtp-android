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
-keepattributes SourceFile,LineNumberTable
-printconfiguration ~/tmp/full-r8-config.txt

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
}
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

-keep class com.walletconnect.* { *; }
-keep class com.walletconnect.wcmodal.client.* { *; }

-keepclasseswithmembernames class org.xmtp.proto.message.contents.* { *; }
-keep class com.sun.jna.* { *; }
-keep class uniffi.xmtpv3.* { *; }

-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window

# Lifecycle
-keep public interface androidx.lifecycle.** { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-dontwarn com.google.android.gms.**
-keep public class com.google.android.gms.**
-keep class com.google.android.gms.common.ConnectionResult {
    int SUCCESS;
}
