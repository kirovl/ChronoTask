package io.github.chronotask.reflect

import io.github.chronotask.exception.ChronoTaskException

/**
 * Thrown when reflective scanning, method binding, or instance registration fails.
 */
class ReflectionRegistrationException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
