# Compatibility Matrix

ChronoTask maintains strict backward binary and source compatibility according to Semantic Versioning 2.0.0.

## Supported Platform Matrix

| Component | Minimum Version | Tested / Primary Version |
| :--- | :--- | :--- |
| **Java JDK** | JDK 17 | JDK 17, JDK 21 |
| **Kotlin (repository build)** | 2.0.0 | 2.0.21 |
| **Kotlin Coroutines** | 1.8.0 | 1.9.0 |
| **KSP (repository build)** | 2.0.0-1.0.21 | 2.0.21-1.0.27 |
| **Gradle** | 8.5 | 8.10.2 |
| **Android SDK** | API 21 (with desugaring) / API 26+ | API 34+ |

## Verified legacy consumer

In addition to the primary build versions above, a standalone JVM application using
Kotlin Gradle plugin 1.9.10 and KSP 1.9.10-1.0.13 has verified ChronoTask 1.0.1 binding
generation, compilation and execution. See the [KSP guide](ksp.md#kotlin-1910-consumers).
The compiler/plugin compatibility check keeps the published transitive runtime
dependencies; it does not claim support for forcing those dependencies to older versions.

## Binary Compatibility Guarantee

Every release is checked with Kotlin Binary Compatibility Validator (`apiCheck`). Public ABI changes are strictly forbidden in Minor and Patch releases.
