# ChronoTask Android Reference Application

This module provides a production-grade reference application demonstrating how to integrate **ChronoTask 1.0.0** in a modern Android application using **Jetpack Compose**, **Material 3**, **KSP**, **Runtime Reflection**, **Java APT**, and **Kotlin DSL**.

---

## 1. Important Architecture Note: In-Process Scheduling Boundaries

> [!IMPORTANT]
> **ChronoTask is an in-process task scheduler.**
> The Android sample demonstrates scheduling while the application process is alive. It does **not** replace system-level background job frameworks like `WorkManager` or `AlarmManager`, and does **not** guarantee execution after process termination.

---

## 2. Key Features Demonstrated

- **Multi-Paradigm Binding Showcase**:
  - **Kotlin Symbol Processing (KSP)**: Direct invocation via `KspSampleTasks_ChronoTaskBinding` with support for `suspend` methods and `TaskExecutionContext` injection.
  - **Runtime Reflection**: Registered dynamically using `engine.registerAnnotated(...)`.
  - **Java APT (JSR-269)**: Direct Java compile-time binding via `AndroidJavaJob_ChronoTaskBinding`.
  - **Kotlin DSL**: Programmatic declarative scheduling (`fixedRate`, `fixedDelay`, `cron`, `queueOne`, `serial`, `onMisfireFireOnce`, `onMisfireCatchUp`).
- **Application-Owned Engine**:
  - The `ChronoTaskEngine` lifecycle is managed by `SampleTaskManager` attached to `ChronoTaskSampleApp : Application()`, preserving task timelines across Activity recreation and configuration changes (e.g. screen rotation).
- **Interactive Compose Material 3 Dashboard**:
  - **Engine Controls**: `Start`, `Pause`, `Resume`, `Stop` (terminal state), and `Recreate Engine`.
  - **Task Cards**: Live status chips (`SCHEDULED`, `RUNNING`, `PAUSED`, `CANCELLED`), run/success/failure/skipped counters, and per-task action buttons (`Trigger Now`, `Pause/Resume`, `Cancel`).
  - **Live Event Stream**: Real-time bounded console (max 500 events) with auto-scrolling.
- **R8 / ProGuard Minification**:
  - Release build with full R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`).
  - Zero blanket keep rules. Uses consumer rules from `chronotask-reflect` for reflection and direct static invocations for KSP/APT.

---

## 3. How to Build & Test

### Debug Build
```bash
./gradlew :sample-android:assembleDebug
```

### Release Minified Build (R8 Verification)
```bash
./gradlew :sample-android:assembleRelease
```

### Run Unit Tests
```bash
./gradlew :sample-android:test
```

---

## 4. UI Dashboard Architecture

```
Application (ChronoTaskSampleApp)
    │
    ▼
SampleTaskManager ──── Holds ────► ChronoTaskEngine (1.0.0)
    │
    ├─► StateFlow<EngineState>
    ├─► StateFlow<List<SampleTaskUiState>>
    └─► StateFlow<List<SampleTaskEvent>> (Bounded 500)
              │
              ▼
    ChronoTaskViewModel (AndroidViewModel)
              │
              ▼
    ChronoTaskDashboardScreen (Jetpack Compose Material 3)
```
