# Keep the engine's public classes and the pluggable image decoder (loaded by name on the desktop, installed directly here).
-keep class io.sajeev.** { *; }
-keep class ai.onnxruntime.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
