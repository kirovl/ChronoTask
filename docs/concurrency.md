# Concurrency Policies

ChronoTask allows fine-grained concurrency control per task to prevent resource exhaustion and data races.

## Available Policies

| Policy | Behavior |
| :--- | :--- |
| `ALLOW` | Allows concurrent overlapping executions of the same task without restriction. |
| `SKIP_IF_RUNNING` | Skips new trigger events if a previous execution of this task is still running. |
| `QUEUE_ONE` | Keeps at most one pending execution waiting if a task is currently running. If multiple triggers arrive, the newest trigger replaces the waiting one. |
| `SERIAL` | Queues triggers in a bounded queue up to `maxQueueSize`. Overflow behavior is governed by `QueueOverflowPolicy` (`DROP_OLDEST`, `DROP_LATEST`, `REJECT`). |

## Example

```kotlin
val handle = engine.schedule("heavy-job") {
    fixedRate(10.seconds)
    // Queue one execution if currently busy
    queueOne()
    execute {
        processHeavyBatch()
    }
}
```
