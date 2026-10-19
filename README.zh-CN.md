# ChronoTask

[English](README.md) | **简体中文**

一个轻量级、基于 Kotlin 协程的进程内任务调度框架，适用于 JVM 上的 Kotlin、Java 以及 Android 应用。

[![许可证](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)](https://openjdk.org)

## 主要特性

- **基于协程**：支持非阻塞等待、结构化并发和任务取消隔离。
- **支持 Java 17+**：提供链式 `TaskBuilder`、Lambda、`Runnable` 和同步生命周期接口。
- **类型安全的 Kotlin DSL**：通过 `@ChronoTaskDsl`、`kotlin.time.Duration` 和扩展函数定义任务。
- **Kotlin 编译期绑定**：通过 `chronotask-ksp` 生成直接调用任务方法的绑定代码，无需运行时反射。
- **Java 编译期绑定**：通过 `chronotask-processor` 和标准 JSR-269 注解处理器生成绑定代码，支持受检异常。
- **可选的运行时反射注册**：`chronotask-reflect` 支持扫描 `@Scheduled`、`@Cron`、`@Once` 和 `@TickTask`。
- **多种调度方式**：支持固定频率、固定延迟、单次延迟、Cron 和自定义触发计算器。
- **灵活的并发策略**：支持 `Allow`、`SkipIfRunning`、`QueueOne` 和带容量及溢出策略的 `Serial` 队列。
- **可配置的错过触发处理**：支持 `Skip`、`FireOnce` 和 `CatchUp(maxExecutions)`。
- **确定性的虚拟时间测试**：使用 `VirtualTimeSource` 测试时间推进、暂停、恢复和长时间跨度的调度行为。
- **独立的核心引擎**：核心模块基于 Kotlin 协程，不强制依赖 Spring 等应用框架，也不要求使用反射。

## 快速开始

五个模块均使用 Maven groupId `io.github.kirovl`，当前版本为 `1.0.1`。源码包名仍为 `io.github.chronotask`，导入代码时请使用该包名。

| 模块 | 用途 |
| --- | --- |
| `chronotask-core` | 核心引擎、调度模型、注解、Kotlin DSL 和 Java Builder |
| `chronotask-ksp` | Kotlin 编译期注解处理器 |
| `chronotask-processor` | Java 编译期注解处理器 |
| `chronotask-reflect` | 运行时反射注册 |
| `chronotask-test` | 虚拟时钟、计时器和测试工具 |

在项目的仓库配置中添加 Maven Central：

```kotlin
repositories {
    mavenCentral()
}
```

如果项目使用 `settings.gradle.kts` 统一管理依赖仓库，请将 `mavenCentral()` 放入该文件的 `dependencyResolutionManagement.repositories` 中。

维护者的账号设置、签名配置和五模块统一发布流程，请参阅[发布指南](docs/publishing.md)（英文）。

### 方式一：Kotlin KSP 编译期绑定

适用于 Kotlin 和 Android 项目。以下为 Kotlin JVM 的 Gradle Kotlin DSL 示例，插件版本与本仓库一致：

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

dependencies {
    // 核心引擎、DSL、Builder 和调度注解
    implementation("io.github.kirovl:chronotask-core:1.0.1")

    // 在编译期生成绑定代码，运行时无需反射
    ksp("io.github.kirovl:chronotask-ksp:1.0.1")

    // 包含 VirtualTimeSource 的测试工具
    testImplementation("io.github.kirovl:chronotask-test:1.0.1")
}
```

Android 项目应使用自己的 Android/Kotlin 插件配置，依赖声明方式相同。详见 [Android 接入指南](docs/android.md)。

### 方式二：Java APT 编译期绑定

适用于 Java 17+ 项目：

```kotlin
plugins {
    java
}

dependencies {
    implementation("io.github.kirovl:chronotask-core:1.0.1")

    // 标准 JSR-269 注解处理器，运行时无需反射
    annotationProcessor("io.github.kirovl:chronotask-processor:1.0.1")

    testImplementation("io.github.kirovl:chronotask-test:1.0.1")
}
```

### 方式三：运行时反射注册

适用于需要在运行时扫描 Kotlin 或 Java 任务实例的项目：

```kotlin
dependencies {
    implementation("io.github.kirovl:chronotask-core:1.0.1")
    implementation("io.github.kirovl:chronotask-reflect:1.0.1")
    testImplementation("io.github.kirovl:chronotask-test:1.0.1")
}
```

## 使用指南

任务需要先注册，再调用 `engine.start()` 启动引擎；添加依赖或声明注解不会自动启动任务。请将引擎保存在应用生命周期内，关闭时调用 `engine.stop()`。

独立 JVM 程序还需要保持进程运行，不能在启动引擎后立即退出 `main()`。完整的启动和退出示例可参考 [Java 示例](sample-java/)。

`1.0.1` 修复了首次触发语义：固定频率、固定延迟和单次任务从首次开始自动调度时计算初始延迟，默认 `0` 表示立即触发；注册后尚未启动的等待时间不计入延迟。正常的定时器唤醒延迟也不会导致默认 `SKIP` 一直跳过任务。请将各 ChronoTask 模块从 `1.0.0` 统一升级到 `1.0.1`，详见[变更记录](CHANGELOG.md)和[生命周期说明](docs/lifecycle.md#first-trigger-and-initial-delay)。

### 1. Java 注解处理器（`chronotask-processor`）

编译时会为任务类生成类型安全的绑定类，通过直接方法调用执行任务：

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
        System.out.println("正在清理临时文件……");
    }

    @Cron(expression = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void nightlyReport(TaskExecutionContext context) {
        System.out.println("正在执行夜间报表，任务 ID：" + context.getTaskId());
    }

    public static void main(String[] args) {
        ChronoTaskEngine engine = ChronoTask.create();
        CleanupJobs jobs = new CleanupJobs();

        // 使用编译期生成的 <类名>_ChronoTaskBinding 注册任务
        List<TaskHandle> handles = CleanupJobs_ChronoTaskBinding.register(engine, jobs);
        engine.start();
    }
}
```

### 2. Kotlin 注解处理器（`chronotask-ksp`）

编译时会为 Kotlin 任务类生成绑定对象，支持普通函数和挂起函数：

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
        println("缓存清理完成，计划触发时间：${ctx.trigger.scheduledAt}")
    }

    @Cron(expression = "0 0 2 * * ?", zone = "UTC", id = "nightly-report")
    fun generateNightlyReport() {
        println("正在生成夜间报表……")
    }

    @Once(delayMs = 5_000)
    suspend fun warmUpCaches() {
        println("缓存预热完成。")
    }

    @TickTask(intervalMs = 100)
    fun gameLoop() {
        // 周期性更新逻辑
    }
}

val engine = ChronoTask.create()
val services = SystemServices()

// 使用生成的绑定对象注册任务
val handles = SystemServices_ChronoTaskBinding.register(engine, services)
engine.start()
```

KSP 应配置在声明注解任务的模块中。任务类及其外层类为 `internal` 时，生成的绑定对象也是 `internal`，应在同一模块内注册；跨模块使用时，可以由任务所在模块提供公开的注册入口。详见 [KSP 指南](docs/ksp.md)。

### 3. 运行时反射注册（`chronotask-reflect`）

向引擎传入任务实例，在运行时扫描并注册调度注解：

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.reflect.registerAnnotated

val engine = ChronoTask.create()
val handles = engine.registerAnnotated(SystemServices())
engine.start()
```

Java 17+ 示例：

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
        System.out.println("正在同步订单，任务 ID：" + ctx.getTaskId());
    }

    @Cron(expression = "0 30 23 * * ?")
    public void dailySettlement() {
        System.out.println("正在执行每日结算。");
    }

    public static void main(String[] args) {
        ChronoTaskEngine engine = ChronoTask.create();
        OrderService service = new OrderService();

        List<TaskHandle> handles = AnnotatedTaskRegistrar.register(engine, service);
        engine.start();
    }
}
```

### 4. Kotlin DSL

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.dsl.schedule
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

val engine = ChronoTask.create()

// 固定频率任务
val heartbeatHandle = engine.schedule("heartbeat") {
    name = "系统心跳"
    fixedRate(interval = 5.seconds, initialDelay = 1.seconds)
    skipIfRunning()
    onMisfireSkip()
    execute { ctx ->
        println("心跳触发时间：${ctx.trigger.scheduledAt}")
    }
}

// 每小时执行一次的 Cron 任务
val cleanupHandle = engine.schedule("hourly-cleanup") {
    cron("0 0 * * * ?", ZoneId.of("UTC"))
    queueOne()
    onMisfireFireOnce()
    execute { ctx ->
        println("正在清理日志，计划触发时间：${ctx.trigger.scheduledAt}")
    }
}

engine.start()
```

### 5. Java 链式 Builder

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

        // 使用 Runnable 注册固定延迟任务
        TaskDefinition backupTask = ChronoTask.newTask("db-backup")
                .name("数据库备份")
                .fixedDelay(Duration.ofMinutes(10), Duration.ofSeconds(30))
                .serial(5, QueueOverflowPolicy.DROP_OLDEST)
                .onMisfireCatchUp(5)
                .executeRunnable(() -> System.out.println("数据库备份完成。"))
                .build();
        TaskHandle backupHandle = engine.register(backupTask);

        // 注册可读取执行上下文的 Cron 任务
        TaskDefinition reportTask = ChronoTask.newTask("daily-report")
                .cron("0 0 9 * * ?", ZoneId.of("Asia/Shanghai"), CronDialect.QUARTZ)
                .allowConcurrent()
                .execute(ctx -> System.out.println("报表触发时间：" + ctx.getTrigger().getScheduledAt()))
                .build();
        TaskHandle reportHandle = engine.register(reportTask);

        engine.start();
    }
}
```

### 6. 面向对象的任务抽象（`ChronoJob` 和 `ManagedCronJob`）

```kotlin
import io.github.chronotask.job.ChronoJob
import io.github.chronotask.job.ManagedCronJob
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskSchedule
import java.time.ZoneId

// 通用任务
class MetricSyncJob : ChronoJob() {
    override val id = "metric-sync"
    override val schedule = TaskSchedule.FixedRate(30_000L)
    override val concurrencyPolicy = ConcurrencyPolicy.QueueOne

    override suspend fun execute(context: TaskExecutionContext) {
        // 支持挂起的业务逻辑
    }
}

// 专用于 Cron 调度的任务
class DailySettlementJob : ManagedCronJob(
    id = "daily-settlement",
    cron = "0 30 23 * * ?",
    zoneId = ZoneId.of("Asia/Shanghai")
) {
    override suspend fun execute(context: TaskExecutionContext) {
        // 每日结算逻辑
    }
}

// 注册到已有引擎
engine.register(MetricSyncJob())
engine.register(DailySettlementJob())
```

## KSP、Java APT 与反射方式对比

| 对比项 | `chronotask-ksp` | `chronotask-processor` | `chronotask-reflect` |
| --- | --- | --- | --- |
| 目标语言 | Kotlin | Java | Kotlin 和 Java |
| 任务发现时机 | 编译期（KSP） | 编译期（JSR-269 APT） | 运行时注册阶段 |
| 调用方式 | 生成代码直接调用 `target.method()` | 生成代码直接调用 `target.method()` | Kotlin 反射或 Java `Method.invoke` |
| 反射开销 | 任务绑定不使用运行时反射 | 任务绑定不使用运行时反射 | 扫描和调用时存在反射开销 |
| 运行时依赖 | 不需要 KSP 处理器，仅需核心库及其传递依赖 | 不需要 APT 处理器，仅需核心库及其传递依赖 | 反射模块及其传递依赖，包括 `kotlin-reflect` |
| R8 / ProGuard | 生成绑定通过直接调用关联任务，无需为反射额外保留 | 生成绑定通过直接调用关联任务，无需为反射额外保留 | 需要保留调度注解及方法，模块已附带消费端规则 |
| 错误诊断 | KSP 编译期诊断 | javac 编译期诊断 | 运行时抛出 `TaskConfigurationException` 等异常 |
| 类型检查 | 类型安全的 `definitions(target)` | 类型安全的 `definitions(target)` | 接收 `Any` / `Object`，运行时校验 |

## 并发策略

| 策略 | 行为 |
| --- | --- |
| `Allow` | 允许同一任务的多个执行并发运行，不限制同时执行的数量。 |
| `SkipIfRunning`（默认） | 任务已有执行正在运行时，跳过新的触发，跳过原因为 `SkipReason.ALREADY_RUNNING`。 |
| `QueueOne` | 最多保留一个等待执行的触发；后续触发替换当前等待的触发。 |
| `Serial(maxQueueSize, overflowPolicy)` | 按 FIFO 顺序串行执行，支持队列容量以及 `DROP_OLDEST`、`DROP_LATEST`、`REJECT` 溢出策略。 |

详见[并发策略指南](docs/concurrency.md)。

## 错过触发的处理策略（Misfire）

当系统休眠、调度延迟等原因导致错过原定触发时间时，可以选择以下处理方式：

| 策略 | 行为 |
| --- | --- |
| `Skip`（默认） | 跳过已经错过的触发，等待下一个正常调度时间点。 |
| `FireOnce` | 补发一次触发，然后恢复正常的调度时间线。 |
| `CatchUp(maxExecutions)` | 按时间顺序补发最多 `maxExecutions` 次触发；实际执行仍受并发策略约束。 |

详见[错过触发处理指南](docs/misfire.md)。

## 使用 `VirtualTimeSource` 进行确定性测试

通过虚拟时钟和计时器推进时间，无需等待真实的调度间隔：

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.dsl.schedule
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class TimelineTest {
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
        assertEquals(1, count) // T+0 时立即触发

        timeSource.advanceBy(java.time.Duration.ofSeconds(20))
        assertEquals(3, count) // T+10 秒和 T+20 秒分别触发一次

        engine.stop()
    }
}
```

JUnit 5 项目还需在测试任务中启用 `useJUnitPlatform()`。更多示例见[测试指南](docs/testing.md)。

## 示例应用

| 示例 | 平台 | 演示内容 | 入口 |
| --- | --- | --- | --- |
| `sample-java` | Java 17+ JVM | Java APT、链式 Builder、并发策略、失败隔离、启动与退出管理。 | [Java 示例](sample-java/) |
| `sample-android` | Android，最低 API 26，目标 API 34 | Jetpack Compose、KSP、运行时反射、Kotlin DSL、R8 混淆和基于 StateFlow 的任务面板。 | [Android 示例](sample-android/) |
| `sample-kotlin-ksp` | Kotlin JVM | 多任务类绑定、挂起任务和 KSP 编译期生成代码。 | [Kotlin KSP 示例](sample-kotlin-ksp/) |

> [!NOTE]
> **进程内调度边界**：ChronoTask 的任务及调度状态保存在当前进程中。Android 任务如果需要在进程被终止后仍由系统安排执行，应结合 WorkManager 等系统调度机制。

## 更多文档

以下详细指南目前为英文：

- [快速入门](docs/getting-started.md)
- [调度注解](docs/annotations.md)
- [Kotlin DSL](docs/kotlin-dsl.md)
- [Java API](docs/java-api.md)
- [KSP 编译期绑定](docs/ksp.md)
- [Java APT 编译期绑定](docs/apt.md)
- [运行时反射](docs/reflection.md)
- [Cron 表达式与时区](docs/cron.md)
- [引擎生命周期](docs/lifecycle.md)
- [Android 接入](docs/android.md)
- [兼容性说明](docs/compatibility.md)
- [Maven Central 发布流程](docs/publishing.md)

Cron 支持 Quartz 和 UNIX 方言，但并不包含所有 Quartz 扩展语法；`1.0.1` 不支持 `L`、`W`、`#`。接入前请结合 Cron 指南和兼容性说明确认适用范围。

## 许可证

采用 Apache License 2.0，详见 [LICENSE](LICENSE)。
