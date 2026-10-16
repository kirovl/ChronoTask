# ChronoTask 1.0.0 Release Notes

We are excited to announce the General Availability (GA) of **ChronoTask 1.0.0**!

ChronoTask is a modern, lightweight, high-performance in-memory task scheduling framework designed specifically for Kotlin and Java on the JVM and Android.

---

## Key Features

1. **Unified Scheduling Architecture**:
   - `FixedRate` (planned timeline) and `FixedDelay` (completion-dependent interval) scheduling.
   - Built-in Cron engine supporting Quartz and UNIX expressions with anti-drift field-jumping calculation and full DST gap/overlap support.
   - One-shot tasks (`Once`) and tick-based tasks (`TickTask`).

2. **Enterprise Concurrency & Misfire Control**:
   - Concurrency policies: `ALLOW`, `SKIP_IF_RUNNING`, `QUEUE_ONE` (latest trigger wins), and bounded `SERIAL` with overflow policies (`DROP_OLDEST`, `DROP_LATEST`, `REJECT`).
   - Misfire strategies: `SKIP`, `FIRE_ONCE`, and mathematically bounded `CatchUp(maxExecutions)`.

3. **Three Annotation Paths with Exact Semantic Equivalence**:
   - **KSP (Kotlin)**: `chronotask-ksp` generates direct-call bindings with 0% runtime reflection overhead.
   - **APT (Java)**: `chronotask-processor` generates standard Java bindings with direct checked exception passthrough.
   - **Reflection**: `chronotask-reflect` provides instant runtime annotation registration with bundled R8/ProGuard consumer rules.

4. **Deterministic Testing Suite**:
   - `chronotask-test` provides `VirtualTimeSource`, `FakeChronoClock`, and `VirtualChronoTimer` to simulate years of scheduler operations in milliseconds without `Thread.sleep`.

5. **Android & JVM First-Class Compatibility**:
   - Full Java 17+ and Android (API 21+ with desugaring / API 26+) compatibility.
   - Consumer ProGuard/R8 rules bundled for seamless release shrinking.

---

## Known Boundaries & Non-Goals

- **In-Memory**: ChronoTask is an in-memory task scheduler. State is not automatically persisted across JVM process restarts.
- **No Distributed Clustering**: Distributed consensus, leader election, and distributed locking are intentionally excluded from the core library.
- **Cron Grammar**: Special Quartz characters `L`, `W`, and `#` are not supported in 1.0.0.

---

## Maven Coordinates

```groovy
// Core runtime
implementation "io.github.chronotask:chronotask-core:1.0.0"

// Optional Reflection binding
implementation "io.github.chronotask:chronotask-reflect:1.0.0"

// Optional Kotlin KSP processor
ksp "io.github.chronotask:chronotask-ksp:1.0.0"

// Optional Java Annotation processor
annotationProcessor "io.github.chronotask:chronotask-processor:1.0.0"

// Testing utilities
testImplementation "io.github.chronotask:chronotask-test:1.0.0"
```
