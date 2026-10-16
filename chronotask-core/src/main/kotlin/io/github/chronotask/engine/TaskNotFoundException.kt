package io.github.chronotask.engine

import io.github.chronotask.exception.ChronoTaskException

/**
 * Thrown when a task lookup by ID fails because no task exists with that ID.
 */
class TaskNotFoundException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
