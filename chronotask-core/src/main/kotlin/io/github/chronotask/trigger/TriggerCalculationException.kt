package io.github.chronotask.trigger

import io.github.chronotask.exception.ChronoTaskException

/**
 * Thrown when a trigger calculation fails or encounters an invalid schedule state.
 */
class TriggerCalculationException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
