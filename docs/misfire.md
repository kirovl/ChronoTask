# Misfire Policies

When system suspension, sleep, or thread starvation causes scheduled triggers to be delayed past their target time, ChronoTask's `MisfireResolver` decides how to recover.

## Available Policies

| Policy | Behavior |
| :--- | :--- |
| `SKIP` | Drops all missed executions and aligns the schedule timeline with the next future trigger. |
| `FIRE_ONCE` | Fires exactly one compensatory execution immediately, then resumes normal scheduling. |
| `CatchUp(maxExecutions)` | Sequentially executes missed triggers up to a strict bound of `maxExecutions` (default: 100), preventing thread storms. |

For `CatchUp`, missed triggers beyond the limit are discarded, and the schedule resumes
at the next regular trigger. The limit applies to the whole missed interval rather than
each batch of trigger calculations. Trigger times strictly before the current instant
are missed, including when they are overdue by less than one millisecond. A trigger
exactly at the current instant remains on time.

For `FixedDelay`, `SKIP` discards the overdue trigger and schedules the next attempt at
the current time plus the configured delay. It does not terminate the recurring task.
After an execution completes, the next delay is measured from its completion time.

Cron recovery locates the latest missed trigger without scanning every occurrence in
the offline interval. `SKIP` and `FIRE_ONCE` remain bounded for long outages;
`CatchUp` generates only the configured number of missed triggers. Cron searches
timezone transition boundaries before continuing field jumps, preserving matching
occurrences in repeated local-time intervals, including half-hour DST overlaps.

## Example

```kotlin
val handle = engine.schedule("sync-data") {
    fixedRate(1.minutes)
    misfireCatchUp(maxExecutions = 5)
    execute {
        sync()
    }
}
```
