# Kotlin DSL Guide

ChronoTask provides a rich, type-safe DSL (`@ChronoTaskDsl`) for defining and registering tasks.

## Basic Usage

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.dsl.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val engine = ChronoTask.create()

val handle = engine.schedule("metric-collector") {
    // Schedule type
    fixedRate(interval = 10.seconds, initialDelay = 1.seconds)

    // Concurrency control
    skipIfRunning()

    // Misfire handling
    misfireSkip()

    // Task execution (supports suspend functions)
    execute { context ->
        println("Collecting metrics for scheduled instant: ${context.scheduledAt}")
    }
}

engine.start()
```

## Schedule Types

- **`fixedRate(interval, initialDelay)`**: Runs at regular intervals based on planned timeline.
- **`fixedDelay(delay, initialDelay)`**: Runs with a fixed delay relative to the completion of the previous execution.
- **`cron(expression, zoneId, dialect)`**: Runs according to a Quartz (6/7 fields) or UNIX (5 fields) cron expression.
- **`once(delay)`**: Runs once after an initial delay.
