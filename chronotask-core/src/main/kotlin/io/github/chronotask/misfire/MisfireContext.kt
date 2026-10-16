package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import java.time.Instant

/**
 * Contextual information provided to a [MisfireResolver] to resolve missed schedule trigger points.
 *
 * @property schedule The task's schedule configuration.
 * @property misfirePolicy The configured misfire recovery policy.
 * @property triggerContext The trigger context containing task execution history timestamps.
 * @property firstScheduledAt The first planned trigger instant that was detected as missed (`firstScheduledAt < now`).
 * @property now The single time snapshot representing current evaluation time.
 */
data class MisfireContext @JvmOverloads constructor(
    val schedule: TaskSchedule,
    val misfirePolicy: MisfirePolicy,
    val triggerContext: TriggerContext,
    val firstScheduledAt: Instant,
    val now: Instant = triggerContext.now
) {
    init {
        require(firstScheduledAt.isBefore(now)) {
            "firstScheduledAt ($firstScheduledAt) must be strictly before now ($now) for misfire resolution"
        }
    }
}
