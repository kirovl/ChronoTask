package io.github.chronotask.trigger

import java.time.Instant

/**
 * SPI for computing the next planned trigger instant for a schedule.
 * Functional interface compatible with Java lambdas.
 */
fun interface TriggerCalculator {

    /**
     * Calculates the next planned trigger instant given the [context].
     *
     * A non-null result must be strictly later than [TriggerContext.lastScheduledAt]
     * when that timestamp is present. The trigger service rejects non-progressing
     * results with [TriggerCalculationException] to prevent repeated dispatch.
     *
     * @param context The current trigger calculation context.
     * @return The next planned trigger instant, or null if no further triggers should occur.
     */
    fun nextTrigger(context: TriggerContext): Instant?
}
