package io.github.chronotask.cron

import io.github.chronotask.exception.ChronoTaskException

/**
 * Exception thrown when a Cron expression string fails parsing or semantic validation.
 *
 * @property expression The raw cron expression string that caused the error.
 * @property dialect The dialect used when parsing the expression.
 * @property reason Detailed reason describing the syntax or validation failure.
 */
class CronParseException @JvmOverloads constructor(
    val expression: String,
    val dialect: CronDialect?,
    val reason: String,
    cause: Throwable? = null
) : ChronoTaskException("Failed to parse Cron expression '$expression' for dialect $dialect: $reason", cause)
