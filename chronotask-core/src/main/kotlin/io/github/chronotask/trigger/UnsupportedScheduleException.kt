package io.github.chronotask.trigger

import io.github.chronotask.exception.ChronoTaskException

/**
 * Thrown when a schedule type is not supported by the runtime.
 */
class UnsupportedScheduleException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
