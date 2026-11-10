# ProGuard rules for Keybord IME

# Keep native methods and JNI bridge
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.keyboard.engine.bridge.** { *; }
-keep class com.keybord.engine.bridge.** { *; }

# Keep data classes and models serialized to JSON
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Keep SQLite and AndroidX components
-dontwarn androidx.**
