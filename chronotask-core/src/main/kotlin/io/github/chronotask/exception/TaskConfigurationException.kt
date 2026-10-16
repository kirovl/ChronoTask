package io.github.chronotask.exception

/**
 * Exception thrown when a task definition or schedule configuration fails semantic or validation checks
 * during DSL/Builder construction or engine registration.
 */
class TaskConfigurationException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : ChronoTaskException(message, cause)
