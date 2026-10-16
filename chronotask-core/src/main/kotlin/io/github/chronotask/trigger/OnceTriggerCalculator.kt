package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import java.time.Instant

/**
 * Calculates planned trigger timestamps for [TaskSchedule.Once].
 *
 * Implements one-shot scheduling:
 * - First trigger: `registeredAt + delayMs`
 * - Subsequent triggers: `null` (schedule terminates)
 */
class OnceTriggerCalculator(
    val schedule: TaskSchedule.Once
) : TriggerCalculator {

    override fun nextTrigger(context: TriggerContext): Instant? {
        return if (context.lastScheduledAt == null) {
            context.registeredAt.plusMillis(schedule.delayMs)
        } else {
            null
        }
    }
}
