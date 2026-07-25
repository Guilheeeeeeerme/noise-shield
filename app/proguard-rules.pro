# Keep native JNI
-keep class com.noiseshield.audio.NativeAudioEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
