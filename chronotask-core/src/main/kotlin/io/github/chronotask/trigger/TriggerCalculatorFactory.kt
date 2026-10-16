package io.github.chronotask.trigger

import io.github.chronotask.cron.CronTriggerCalculator
import io.github.chronotask.model.TaskSchedule

/**
 * Factory for creating appropriate [TriggerCalculator] implementations for given [TaskSchedule] types.
 */
object TriggerCalculatorFactory {

    /**
     * Creates a [TriggerCalculator] matching the provided [schedule].
     */
    @JvmStatic
    fun create(schedule: TaskSchedule): TriggerCalculator = when (schedule) {
        is TaskSchedule.FixedRate -> FixedRateTriggerCalculator(schedule)
        is TaskSchedule.FixedDelay -> FixedDelayTriggerCalculator(schedule)
        is TaskSchedule.Once -> OnceTriggerCalculator(schedule)
        is TaskSchedule.Custom -> CustomTriggerCalculator(schedule)
        is TaskSchedule.Cron -> CronTriggerCalculator(schedule)
    }
}
