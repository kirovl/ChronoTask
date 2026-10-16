# ChronoTask Reflection Consumer Proguard / R8 Rules

# Preserve ChronoTask scheduling annotations
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep,allowobfuscation @interface io.github.chronotask.annotation.** { *; }

# Preserve methods annotated with ChronoTask scheduling annotations and their declaring classes
-keepclassmembers class * {
    @io.github.chronotask.annotation.Scheduled <methods>;
    @io.github.chronotask.annotation.Cron <methods>;
    @io.github.chronotask.annotation.Once <methods>;
    @io.github.chronotask.annotation.TickTask <methods>;
}

# Retain Kotlin Metadata for reflective inspection of suspend functions
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
