# ChronoTask

**English** | [简体中文](README.zh-CN.md)

A modern, lightweight, coroutine-native task scheduling framework for Kotlin and Java on the JVM.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)](https://openjdk.org)

---

## Features

- **Coroutine Native**: Built from the ground up on Kotlin Coroutines with non-blocking delays, structural concurrency, and cancellation isolation.
- **First-Class Java 17+ Interop**: Fluent `TaskBuilder`, lambda / `Runnable` execution, and synchronous lifecycle methods.
- **Type-Safe Kotlin DSL**: Expressive `@ChronoTaskDsl` builder syntax with `kotlin.time.Duration` and type-safe extensions.
- **Compile-Time KSP Code Generation**: Zero-overhead direct executable binding for Kotlin via Kotlin Symbol Processing (`chronotask-ksp`).
- **Compile-Time Java Annotation Processing (APT)**: Zero-overhead direct executable binding for Java via JSR-269 standard processor (`chronotask-processor`).
- **Declarative Runtime Annotation API**: Optional runtime reflection scanner (`chronotask-reflect`) supporting `@Scheduled`, `@Cron`, `@Once`, and `@TickTask`.
- **Standard Schedule Types**: Fixed Rate, Fixed Delay, One-time Delay, Programmatic Custom Calculators, and Full Cron (Quartz & Unix dialects).
- **Flexible Concurrency Policies**: `Allow`, `SkipIfRunning`, `QueueOne`, `Serial` (with `DROP_OLDEST`, `DROP_LATEST`, `REJECT`).
- **Configurable Misfire Handling**: `Skip`, `FireOnce`, `CatchUp(maxExecutions)`.
- **Deterministic Virtual Time Testing**: Test complex multi-step timelines, pause/resumes, and year-long schedules in milliseconds using `VirtualTimeSource`.
- **Zero Heavyweight Dependencies**: Core module has zero reflection overhead and no mandatory framework ties.

---

## Quick Start

For maintainer setup, signing credentials and publishing all five modules to Maven
Central, see the [publishing guide](docs/publishing.md).

### Gradle (Kotlin DSL)

#### Option A: Compile-Time KSP (Recommended for Kotlin / Android)

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

dependencies {
    // Core engine, DSL, builders, and annotation contracts
    implementation("io.github.kirovl:chronotask-core:1.0.1")

    // KSP processor for compile-time direct executable bindings (Zero Reflection)
    ksp("io.github.kirovl:chronotask-ksp:1.0.1")

    // Testing utilities with VirtualTimeSource
    testImplementation("io.github.kirovl:chronotask-test:1.0.1")
}
```

#### Option B: Compile-Time Java APT (Recommended for Java 17+)

```kotlin
plugins {
    java
}

dependencies {
    // Core engine
    implementation("io.github.kirovl:chronotask-core:1.0.1")

    // Standard Java Annotation Processor (Zero Reflection, JSR-269)
    annotationProcessor("io.github.kirovl:chronotask-processor:1.0.1")

    testImplementation("io.github.kirovl:chronotask-test:1.0.1")
}
```

#### Option C: Runtime Reflection (Dynamic scanning for Kotlin & Java)

```kotlin
dependencies {
    implementation("io.github.kirovl:chronotask-core:1.0.1")
    implementation("io.github.kirovl:chronotask-reflect:1.0.1")
    testImplementation("io.github.kirovl:chronotask-test:1.0.1")
}
```

---

## Usage Guide

Version `1.0.1` fixes startup scheduling: relative initial delays start when
automatic scheduling first activates, and normal timer wakeup lateness does not cause
default `SKIP` to starve recurring tasks. Upgrade all ChronoTask modules together from
`1.0.0` to `1.0.1`; see the [changelog](CHANGELOG.md) and
[lifecycle guide](docs/lifecycle.md#first-trigger-and-initial-delay).

### 1. Compile-Time Java Annotation Processing (`chronotask-processor`)

Using `chronotask-processor`, ChronoTask generates direct, typed binding classes at compile-time with **zero reflection** and **zero runtime overhead**:

```java
package com.example.jobs;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.annotation.*;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.job.TaskExecutionContext;

import java.io.IOException;
import java.util.List;

public class CleanupJobs {

    @Scheduled(intervalMs = 10_000L, mode = ScheduleMode.FIXED_RATE)
    public void cleanup() throws IOException {
        System.out.println("Cleaning temporary files...");
    }

    @Cron(expression = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void nightlyReport(TaskExecutionContext context) {
        System.out.println("Running nightly report for trigger: " + context.getTaskId());
    }

    public static void main(String[] args) {
        ChronoTaskEngine engine = ChronoTask.create();
        CleanupJobs jobs = new CleanupJobs();

        // Registration using compile-time generated binding: <ClassName>_ChronoTaskBinding
        List<TaskHandle> handles = CleanupJobs_ChronoTaskBinding.register(engine, jobs);
        engine.start();
    }
}
```

---

### 2. Compile-Time Kotlin Annotation API (`chronotask-ksp`)

Using `chronotask-ksp`, ChronoTask generates direct, typed binding objects for Kotlin classes:

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.*
import io.github.chronotask.job.TaskExecutionContext
import kotlinx.coroutines.delay

class SystemServices {

    @Scheduled(
        intervalMs = 30_000,
        mode = ScheduleMode.FIXED_DELAY,
        concurrency = AnnotationConcurrency.SKIP_IF_RUNNING
    )
    suspend fun cleanCache(ctx: TaskExecutionContext) {
        delay(100)
        println("Cache cleaned at ${ctx.trigger.scheduledAt}")
    }

    @Cron(expression = "0 0 2 * * ?", zone = "UTC", id = "nightly-report")
    fun generateNightlyReport() {
        println("Generating nightly report...")
    }

    @Once(delayMs = 5_000)
    suspend fun warmUpCaches() {
        println("Caches warmed up.")
    }

    @TickTask(intervalMs = 100)
    fun gameLoop() {
        // Fast periodic tick
    }
}

// Registration using generated binding: <ClassName>_ChronoTaskBinding
val engine = ChronoTask.create()
val services = SystemServices()

val handles = SystemServices_ChronoTaskBinding.register(engine, services)
engine.start()
```

---

### 3. Runtime Reflection Annotation API (`chronotask-reflect`)

For applications that prefer dynamic runtime scanning (e.g., Spring or Java runtime reflection):

```kotlin
val engine = ChronoTask.create()
val handles = engine.registerAnnotated(SystemServices())
engine.start()
```

#### Java 17+ Reflection Example

```java
import io.github.chronotask.ChronoTask;
import io.github.chronotask.annotation.Cron;
import io.github.chronotask.annotation.Scheduled;
import io.github.chronotask.annotation.ScheduleMode;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.job.TaskExecutionContext;
import io.github.chronotask.reflect.AnnotatedTaskRegistrar;

import java.util.List;

public class OrderService {

    @Scheduled(intervalMs = 10_000, mode = ScheduleMode.FIXED_RATE)
    public void syncOrders(TaskExecutionContext ctx) {
        System.out.println("Syncing orders for trigger " + ctx.getTaskId());
    }

    @Cron(expression = "0 30 23 * * ?")
    public void dailySettlement() {
        System.out.println("Running daily settlement");
    }

    public static void main(String[] args) {
        ChronoTaskEngine engine = ChronoTask.create();
        OrderService service = new OrderService();

        List<TaskHandle> handles = AnnotatedTaskRegistrar.register(engine, service);
        engine.start();
    }
}
```

---

### 4. Kotlin DSL

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.dsl.schedule
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Create default engine
val engine = ChronoTask.create()

// Schedule fixed-rate task
val heartbeatHandle = engine.schedule("heartbeat") {
    name = "System Heartbeat"
    fixedRate(interval = 5.seconds, initialDelay = 1.seconds)
    skipIfRunning()
    onMisfireSkip()
    execute { ctx ->
        println("Heartbeat at ${ctx.trigger.scheduledAt}")
    }
}

// Schedule Cron task
val cleanupHandle = engine.schedule("hourly-cleanup") {
    cron("0 0 * * * ?", ZoneId.of("UTC"))
    queueOne()
    onMisfireFireOnce()
    execute { ctx ->
        println("Running log cleanup at ${ctx.trigger.scheduledAt}")
    }
}

// Start engine
engine.start()
```

---

### 5. Java Fluent Builder

```java
import io.github.chronotask.ChronoTask;
import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.QueueOverflowPolicy;
import io.github.chronotask.model.TaskDefinition;

import java.time.Duration;
import java.time.ZoneId;

public class App {
    public static void main(String[] args) {
        ChronoTaskEngine engine = ChronoTask.create();

        // Register fixed-delay task with Runnable
        TaskDefinition backupTask = ChronoTask.newTask("db-backup")
                .name("Database Backup")
                .fixedDelay(Duration.ofMinutes(10), Duration.ofSeconds(30))
                .serial(5, QueueOverflowPolicy.DROP_OLDEST)
                .onMisfireCatchUp(5)
                .executeRunnable(() -> System.out.println("Database backup complete."))
                .build();
        TaskHandle backupHandle = engine.register(backupTask);

        // Register Cron task with execution context
        TaskDefinition reportTask = ChronoTask.newTask("daily-report")
                .cron("0 0 9 * * ?", ZoneId.of("Asia/Shanghai"), CronDialect.QUARTZ)
                .allowConcurrent()
                .execute(ctx -> System.out.println("Report triggered: " + ctx.getTrigger().getScheduledAt()))
                .build();
        TaskHandle reportHandle = engine.register(reportTask);

        engine.start();
    }
}
```

---

### 6. Object-Oriented Job Abstractions (`ChronoJob` & `ManagedCronJob`)

```kotlin
// General ChronoJob
class MetricSyncJob : ChronoJob() {
    override val id = "metric-sync"
    override val schedule = TaskSchedule.FixedRate(30_000L)
    override val concurrencyPolicy = ConcurrencyPolicy.QueueOne

    override suspend fun execute(context: TaskExecutionContext) {
        // Suspending business logic
    }
}

// Specialized ManagedCronJob
class DailySettlementJob : ManagedCronJob(
    id = "daily-settlement",
    cron = "0 30 23 * * ?",
    zoneId = ZoneId.of("Asia/Shanghai")
) {
    override suspend fun execute(context: TaskExecutionContext) {
        // Daily settlement logic
    }
}

// Registration
engine.register(MetricSyncJob())
engine.register(DailySettlementJob())
```

---

## Comparison: KSP vs Java APT vs Reflection

| Dimension | `chronotask-ksp` (M9) | `chronotask-processor` (M10) | `chronotask-reflect` (M8) |
|---|---|---|---|
| **Target Language** | Kotlin | Java | Kotlin & Java |
| **Discovery Time** | Compile-Time (KSP) | Compile-Time (JSR-269 APT) | Runtime Startup |
| **Invocation Method** | Direct lambda call (`target.method()`) | Direct method reference (`target.method()`) | `KCallable.callSuspend` / `Method.invoke` |
| **Runtime Overhead** | Zero | Zero | Minor reflection overhead on startup |
| **Runtime Dependencies** | `chronotask-core` only | `chronotask-core` only | `chronotask-reflect` + `kotlin-reflect` |
| **R8 / ProGuard / Native** | No keep rules required | No keep rules required | Requires keep rules for annotated methods |
| **Diagnostics** | Compile-time errors with IDE navigation | Compile-time javac diagnostics | Runtime `TaskConfigurationException` |
| **Typing** | Strongly typed `definitions(target: T)` | Strongly typed `definitions(T target)` | `target: Any` / `Object` |

---

## Concurrency Policies

| Policy | Behavior |
|---|---|
| `Allow` | Triggers execute immediately in parallel without restriction. |
| `SkipIfRunning` *(Default)* | If an execution is already running, new trigger events are skipped (`SkipReason.ALREADY_RUNNING`). |
| `QueueOne` | Holds at most 1 pending execution in queue; subsequent triggers replace the queued trigger. |
| `Serial(maxQueueSize, overflowPolicy)` | Strict serial FIFO execution with configurable capacity and overflow handling (`DROP_OLDEST`, `DROP_LATEST`, `REJECT`). |

---

## Misfire Policies

| Policy | Behavior |
|---|---|
| `Skip` *(Default)* | Discards all missed triggers; next execution fires at the next regular schedule point. |
| `FireOnce` | Immediately dispatches exactly 1 compensatory trigger for missed runs, then resumes regular timeline. |
| `CatchUp(maxExecutions)` | Sequentially executes missed triggers up to `maxExecutions`. |

---

## Deterministic Testing with `VirtualTimeSource`

```kotlin
@Test
fun testTimeline() = runBlocking {
    val baseTime = Instant.parse("2026-01-01T00:00:00Z")
    val timeSource = VirtualTimeSource(baseTime)

    val engine = ChronoTask.create(
        EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)
        )
    )

    var count = 0
    engine.schedule("test-task") {
        fixedRate(interval = 10.seconds)
        execute { count++ }
    }

    engine.start()
    assertEquals(1, count) // Immediate initial trigger at T+0

    timeSource.advanceBy(java.time.Duration.ofSeconds(20))
    assertEquals(3, count) // T+10s and T+20s executed deterministically

    engine.stop()
}
```

---

## Reference Applications & Samples

ChronoTask provides production-grade sample applications showcasing integration across Kotlin, Java, and Android:

| Sample | Platform | Technologies Demonstrated | Link |
|---|---|---|---|
| **`sample-java`** | Pure Java 17+ JVM | JSR-269 APT (`chronotask-processor`), Java Fluent Builder (`ChronoTask.newTask(...)`), Concurrency policies, Failure isolation, Shutdown hooks, Standalone CLI runner. | [`sample-java/`](sample-java/) |
| **`sample-android`** | Android (API 26–34) | Jetpack Compose (Material 3), KSP (`chronotask-ksp`), Runtime Reflection (`chronotask-reflect`), Java APT (`chronotask-processor`), Kotlin DSL, R8 / ProGuard minification, StateFlow Dashboard. | [`sample-android/`](sample-android/) |
| **`sample-kotlin-ksp`** | Kotlin JVM | Dedicated KSP verification suite showcasing multi-class bindings, suspending tasks, and zero-reflection overhead. | [`sample-kotlin-ksp/`](sample-kotlin-ksp/) |

> [!NOTE]
> **In-Process Scheduling Boundary**: ChronoTask is designed for in-process memory-efficient scheduling. For Android background tasks that must survive process termination, pair ChronoTask with OS-level managers (e.g. WorkManager).

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

