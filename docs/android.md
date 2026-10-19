# Android & R8 / ProGuard Guide

ChronoTask is fully compatible with Android (API 21+ with core library desugaring, or API 26+ native).

## Recommended Setup for Android

```kotlin
// In app/build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
}

dependencies {
    // Core runtime
    implementation("io.github.kirovl:chronotask-core:1.0.1")

    // Compile-time binding generation (Zero Reflection)
    ksp("io.github.kirovl:chronotask-ksp:1.0.1")
}
```

## ProGuard / R8 Minification

- **With KSP or APT**: Generated bindings `<ClassName>_ChronoTaskBinding` are direct Kotlin/Java bytecode. They can be safely obfuscated and shrunk by R8 with **zero custom ProGuard rules**.
- **With Reflection (`chronotask-reflect`)**: Consumer rules are automatically bundled in `chronotask-reflect` (`META-INF/com.android.tools/r8/chronotask-reflect.pro`), keeping annotations and reflection target methods intact.

## Lifecycle Ownership

ChronoTask does not bind automatically to Android Activity/Fragment lifecycles. It is recommended to manage the `ChronoTaskEngine` lifecycle inside your Application class, Android Service, or ViewModel.
