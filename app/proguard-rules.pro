# Shizuku instantiates the UserService by class name via reflection
# (in its own process, UID 2000): keep the class, its no-arg constructor and methods.
-keep class com.ultracore.app.shizuku.ShellUserService {
    public <init>();
    public <init>(android.content.Context);
    public <methods>;
}
-keep class com.ultracore.app.shizuku.IShellService { *; }
-keep class com.ultracore.app.shizuku.IShellService$* { *; }
# Parcelable sent over Binder.
-keep class com.ultracore.app.shizuku.ShellResult { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Shizuku library.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# kotlinx.serialization (the library ships rules too; these keep our own models safe).
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.ultracore.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ultracore.app.**$$serializer { *; }

# Readable crash stack traces.
-keepattributes SourceFile, LineNumberTable
