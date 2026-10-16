package io.github.chronotask.trigger

import java.time.Instant

/**
 * Contextual state provided to a [TriggerCalculator] for computing the next planned trigger instant.
 *
 * @property now The current instant observed by the scheduler.
 * @property registeredAt The instant the task was registered into the engine.
 * @property lastScheduledAt The previous planned schedule instant on the timeline, NOT the actual execution start instant. Null on first calculation.
 * @property lastStartedAt The actual instant the [io.github.chronotask.job.TaskExecutable] was started on its last execution. Null if never started.
 * @property lastCompletedAt The actual instant the [io.github.chronotask.job.TaskExecutable] completed its last execution. Null if never completed.
 */
data class TriggerContext(
    val now: Instant,
    val registeredAt: Instant,
    val lastScheduledAt: Instant?,
    val lastStartedAt: Instant?,
    val lastCompletedAt: Instant?
)
