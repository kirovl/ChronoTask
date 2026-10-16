package io.github.chronotask.exception

/**
 * Base root exception class for all ChronoTask runtime exceptions.
 */
open class ChronoTaskException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
