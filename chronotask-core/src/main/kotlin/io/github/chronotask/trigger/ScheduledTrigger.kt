package io.github.chronotask.trigger

import java.time.Instant

/**
 * Encapsulates a concrete trigger event dispatched for a task.
 *
 * @property scheduledAt The planned trigger instant according to the schedule timeline.
 * @property discoveredAt The actual instant the engine detected/dispatched this trigger event.
 * @property source The origin mechanism of the trigger event.
 */
data class ScheduledTrigger @JvmOverloads constructor(
    val scheduledAt: Instant,
    val discoveredAt: Instant,
    val source: TriggerSource = TriggerSource.SCHEDULE
)
