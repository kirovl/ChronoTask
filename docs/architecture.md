# ChronoTask Architecture Specification

ChronoTask is structured into clear, decoupled layers ensuring 100% semantic parity across all Kotlin, Java, DSL, Builder, Reflection, KSP, and APT entrypoints.

## Core Architectural Pipeline

```text
Kotlin DSL / Java Builder / Reflection / KSP / APT
                      │
                      ▼
               TaskDefinition
                      │
                      ▼
                 TaskSchedule
                      │
                      ▼
              TriggerCalculator
                      │
                      ▼
               ScheduledTrigger
                      │
                      ▼
               MisfireResolver
                      │
                      ▼
            ConcurrencyController
                      │
                      ▼
                  TaskRunner
                      │
                      ▼
                TaskExecutable
```

## Architectural Decoupling Principles

1. **Trigger Calculation is Pure**: `TriggerCalculator` implementations (FixedRate, FixedDelay, Cron, Once) produce schedule timelines without mutating execution state.
2. **Misfire Resolution is Independent**: `MisfireResolver` evaluates missed triggers using mathematical boundary calculations without coupling to task runtime execution loops.
3. **Execution Safety**: `TaskRunner` isolates errors from user executables and execution listeners, guaranteeing that no failing task can terminate the engine.
4. **Binding Equivalence**:
   - `chronotask-reflect`: Runtime reflection scanning
   - `chronotask-ksp`: Compile-time Kotlin Symbol Processing
   - `chronotask-processor`: Compile-time Java Annotation Processing
   All three produce identical `TaskDefinition` metadata and share the single core engine.

## Direct Concurrency Controller Submissions

`DefaultConcurrencyController` fixes the concurrency policy for each task ID on its first submission. Later definitions for that ID may change the executable or other metadata, but changing the concurrency policy throws `IllegalArgumentException` before accepting work. This also applies to Serial capacity and overflow settings. To use a different policy, call `cancelAll(taskId)` to cancel existing work and retire its state before submitting the new definition. Engine registrations already keep one fixed definition per registration.

## Custom Execution and Trigger Collaborators

The concurrency controller guarantees one start event and one terminal event for each dispatched execution. It uses a custom runner's returned `ExecutionResult` when the runner omits callbacks and deduplicates runner callbacks with controller events. This preserves execution counters and fixed-delay completion feedback for success, failure, cancellation, and skipped outcomes. The first terminal notification wins.

A custom trigger calculator may return the registration time for its first trigger or `null` to finish. Once `lastScheduledAt` is present, each non-null result must be strictly later. Non-progressing results raise `TriggerCalculationException` and stop only that task's automatic schedule.
