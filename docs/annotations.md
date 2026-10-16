# Annotation API Overview

ChronoTask supports declarative scheduling via four core annotations with three interchangeable execution strategies.

## Core Annotations

| Annotation | Description | Key Attributes |
| :--- | :--- | :--- |
| `@Scheduled` | Fixed rate / fixed delay intervals | `intervalMs`, `delayMs`, `initialDelayMs`, `concurrency`, `misfire` |
| `@Cron` | Quartz or UNIX cron expressions | `expression`, `zone`, `dialect`, `misfire`, `concurrency` |
| `@Once` | One-shot execution | `delayMs`, `concurrency` |
| `@TickTask` | Tick rate / delay intervals | `tickRateMs`, `tickDelayMs`, `initialDelayMs` |

## Three Execution Strategies

1. **`chronotask-ksp` (Recommended for Kotlin / Android)**:
   - Zero reflection overhead at runtime.
   - Generates `<ClassName>_ChronoTaskBinding.kt`.
2. **`chronotask-processor` (Recommended for Java)**:
   - JSR-269 annotation processor with checked exception passthrough.
   - Generates `<ClassName>_ChronoTaskBinding.java`.
3. **`chronotask-reflect` (Dynamic scanning)**:
   - Dynamic reflection registration via `engine.registerAnnotated(target)`.
   - Bundles ProGuard / R8 consumer rules.
