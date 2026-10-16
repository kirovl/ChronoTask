package io.github.chronotask.cron

/**
 * Enumeration of cron field positions and their allowed value bounds.
 */
internal enum class CronFieldType(
    val fieldName: String,
    val min: Int,
    val max: Int
) {
    SECOND("second", 0, 59),
    MINUTE("minute", 0, 59),
    HOUR("hour", 0, 23),
    DAY_OF_MONTH("day-of-month", 1, 31),
    MONTH("month", 1, 12),
    DAY_OF_WEEK("day-of-week", 1, 7), // Standardized: 1=Sunday, 2=Monday, ..., 7=Saturday
    YEAR("year", 1970, 2199)
}
