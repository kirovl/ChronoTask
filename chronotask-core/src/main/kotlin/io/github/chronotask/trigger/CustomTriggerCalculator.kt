package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import java.time.Instant

/**
 * Delegates trigger calculations to user-provided [TriggerCalculator] within [TaskSchedule.Custom].
 *
 * Exceptions thrown by the underlying custom calculator are propagated without interception.
 */
class CustomTriggerCalculator(
    val schedule: TaskSchedule.Custom
) : TriggerCalculator {

    override fun nextTrigger(context: TriggerContext): Instant? {
        return schedule.calculator.nextTrigger(context)
    }
}
