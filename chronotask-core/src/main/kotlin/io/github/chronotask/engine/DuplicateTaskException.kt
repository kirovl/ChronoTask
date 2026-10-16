package io.github.chronotask.engine

import io.github.chronotask.exception.ChronoTaskException

/**
 * Thrown when attempting to register a task with an ID that is already registered in the engine.
 */
class DuplicateTaskException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
