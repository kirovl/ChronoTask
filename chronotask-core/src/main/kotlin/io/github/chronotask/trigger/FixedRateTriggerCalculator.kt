package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import java.time.Instant

/**
 * Calculates planned trigger timestamps for [TaskSchedule.FixedRate].
 *
 * Implements anti-drift scheduling strictly based on [TriggerContext.lastScheduledAt]:
 * - First trigger: `registeredAt + initialDelayMs`
 * - Subsequent triggers: `lastScheduledAt + intervalMs`
 */
class FixedRateTriggerCalculator(
    val schedule: TaskSchedule.FixedRate
) : TriggerCalculator {

    override fun nextTrigger(context: TriggerContext): Instant {
        return if (context.lastScheduledAt == null) {
            context.registeredAt.plusMillis(schedule.initialDelayMs)
        } else {
            context.lastScheduledAt.plusMillis(schedule.intervalMs)
        }
    }
}
