# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\e036307\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate

-keep class com.ndsthreeds.android.sdk.** { *; }
#-keep class org.spongycastle.** { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.RSA$Mappings** { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.rsa.PSSSignatureSpi$SHA256withRSA** { *; }
-keep class org.spongycastle.jcajce.provider.symmetric.AES$AlgParamsGCM**
-keep class org.spongycastle.jcajce.provider.symmetric.AES$ECB**
-keep class org.spongycastle.jcajce.provider.symmetric.AES$Mappings**

# These lines needed for play services lib
-keep class * extends java.util.ListResourceBundle {
    protected Object[][] getContents();
}
-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
