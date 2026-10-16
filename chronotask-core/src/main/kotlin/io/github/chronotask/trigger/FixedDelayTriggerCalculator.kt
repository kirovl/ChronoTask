package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import java.time.Instant

/**
 * Calculates planned trigger timestamps for [TaskSchedule.FixedDelay].
 *
 * Uses execution completion as the delay anchor, with [TriggerContext.lastScheduledAt]
 * as a fallback when a trigger is skipped:
 * - First trigger: `registeredAt + initialDelayMs`
 * - Subsequent triggers: `lastCompletedAt + delayMs`, or the skipped timeline anchor
 *   plus `delayMs` when the previous trigger did not execute.
 */
class FixedDelayTriggerCalculator(
    val schedule: TaskSchedule.FixedDelay
) : TriggerCalculator {

    override fun nextTrigger(context: TriggerContext): Instant {
        val completed = context.lastCompletedAt
        val scheduled = context.lastScheduledAt
        val anchor = when {
            completed == null -> scheduled
            scheduled == null -> completed
            else -> maxOf(completed, scheduled)
        }
        return if (anchor == null) {
            context.registeredAt.plusMillis(schedule.initialDelayMs)
        } else {
            anchor.plusMillis(schedule.delayMs)
        }
    }
}
