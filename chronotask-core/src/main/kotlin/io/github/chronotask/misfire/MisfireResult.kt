package io.github.chronotask.misfire

import io.github.chronotask.trigger.ScheduledTrigger
import java.time.Instant

/**
 * Result produced by a [MisfireResolver] after handling missed triggers.
 *
 * @property triggers The compensatory [ScheduledTrigger] instances to be dispatched downstream.
 * @property nextScheduledAt The advanced future schedule instant on the normal timeline, or null if the schedule terminated.
 */
data class MisfireResult @JvmOverloads constructor(
    val triggers: List<ScheduledTrigger>,
    val nextScheduledAt: Instant?,
    val alignedLastScheduledAt: Instant? = null
)
