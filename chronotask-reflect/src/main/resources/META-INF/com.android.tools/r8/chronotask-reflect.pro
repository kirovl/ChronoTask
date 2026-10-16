# ChronoTask Reflection Consumer Rules for ProGuard / R8
# Retain ChronoTask annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class io.github.chronotask.annotation.** { *; }

# Keep methods annotated with ChronoTask scheduling annotations
-keepclassmembers class * {
    @io.github.chronotask.annotation.Scheduled <methods>;
    @io.github.chronotask.annotation.Cron <methods>;
    @io.github.chronotask.annotation.Once <methods>;
    @io.github.chronotask.annotation.TickTask <methods>;
}

# Keep Kotlin metadata if kotlin-reflect is used on reflection targets
-keep class kotlin.Metadata { *; }
