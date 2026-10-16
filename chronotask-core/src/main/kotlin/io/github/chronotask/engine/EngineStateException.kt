package io.github.chronotask.engine

import io.github.chronotask.exception.ChronoTaskException

/**
 * Thrown when an engine operation is invalid for its current state.
 */
class EngineStateException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
