# Proguard rules for Kaiko
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
