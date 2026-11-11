# ProGuard rules for Keybord IME

# Preserve clean stack traces for About & Diagnostics crash log
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep native methods and JNI bridge
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.keyboard.engine.bridge.** { *; }
-keep class com.keybord.engine.bridge.** { *; }

# Keep Keybord IME service, views, diagnostics, and domain models
-keep class com.keybord.ime.KeybordIME { *; }
-keep class com.keybord.ime.** { *; }
-keep class com.keybord.diagnostics.** { *; }
-keep class com.keybord.theme.** { *; }
-keep class com.keybord.profile.** { *; }
-keep class com.keybord.clipboard.** { *; }
-keep class com.keybord.engine.** { *; }

# Keep enum values()/valueOf() methods under R8 shrinking
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep custom View constructors
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep data classes and models serialized to JSON
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Keep SQLite, AndroidX, and JSON warnings suppressed cleanly
-dontwarn androidx.**
-dontwarn org.json.**
-dontwarn kotlinx.coroutines.**
