# ChronoTask Java 17+ Reference Application

This module provides a complete, standalone reference application demonstrating how to use **ChronoTask 1.0.0** in a pure Java 17+ JVM environment with zero Kotlin runtime dependencies in business logic.

---

## 1. Key Features Demonstrated

- **Annotation Processing (APT / JSR-269)**:
  - `@Scheduled` (FixedRate and FixedDelay modes)
  - `@Cron` (Quartz / Spring / Unix cron expressions)
  - `@Once` (One-shot delayed execution)
  - `@TickTask` (High-precision rate-based periodic ticks)
  - `TaskExecutionContext` (Execution context injection)
  - Checked Exception handling
- **Programmatic Builder API**:
  - `ChronoTask.newTask(...)` for runtime task construction.
  - Concurrency policies (`SKIP_IF_RUNNING`, `QUEUE_ONE`).
  - Misfire policies (`MisfirePolicy.FIRE_ONCE`).
- **Failure & Exception Isolation**:
  - Task exceptions are safely captured by `TaskErrorHandler` while the engine and other scheduled tasks continue executing seamlessly.
- **Graceful Shutdown**:
  - Integration with JVM shutdown hooks (`Runtime.getRuntime().addShutdownHook(...)`).
- **Zero Runtime Processor Leakage**:
  - `chronotask-processor` operates strictly at compile-time (`annotationProcessor`).

---

## 2. Requirements

- JDK 17 or higher.

---

## 3. How to Run

### Continuous Mode (Interactive)
```bash
./gradlew :sample-java:run
```
Press `Ctrl+C` to initiate a graceful shutdown.

### Finite Duration Mode (CI / Automated testing)
```bash
./gradlew :sample-java:run --args="--duration=10s"
```
Or via environment variable:
```bash
SAMPLE_DURATION_SECONDS=10 ./gradlew :sample-java:run
```

---

## 4. Expected Console Output

```text
==================================================================
           ChronoTask Java 17+ Reference Application              
==================================================================
Version: 1.0.0
Execution Mode: Continuous (Ctrl+C to stop)
------------------------------------------------------------------
Registered tasks:
 - order-sync-job                (APT: FixedRate 2s, initialDelay 1s)
 - inventory-check-job          (APT: FixedDelay 1.5s)
 - cron-heartbeat-job           (APT: Cron */5 * * * * ?)
 - welcome-job                  (APT: Once 500ms)
 - tick-beacon-job              (APT: TickTask 1s)
 - context-audit-job            (APT: FixedRate 4s with TaskExecutionContext)
 - failure-demo-job             (APT: FixedRate 3s, Error Isolation Demo)
 - slow-task-job                (APT: FixedRate 1s, SkipIfRunning Demo)
 - java-builder-archive-task    (Builder: FixedRate 3s, Misfire FireOnce)
 - java-builder-queue-one-task  (Builder: FixedRate 6s, QueueOne)
------------------------------------------------------------------
[ChronoTask-Java] Starting engine...
[ChronoTask-Java] Engine state: RUNNING. Tasks executing automatically.

12:00:00.512 [welcome-job           ] [DefaultDispatcher-worker-1] SUCCESS  One-shot startup initialization completed
12:00:01.005 [tick-beacon-job       ] [DefaultDispatcher-worker-2] SUCCESS  Tick beacon pulse (#1)
12:00:01.010 [order-sync-job        ] [DefaultDispatcher-worker-3] SUCCESS  Order sync processed batch #1
12:00:01.015 [slow-task-job         ] [DefaultDispatcher-worker-4] START    Starting heavy batch work #1 (takes ~2.5s)
12:00:01.508 [inventory-check-job   ] [DefaultDispatcher-worker-1] SUCCESS  Inventory check completed (#1)
...
```
