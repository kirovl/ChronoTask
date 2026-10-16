package io.github.chronotask.cron

/**
 * Dialect format for Cron expressions.
 */
enum class CronDialect {
    /**
     * Standard 6 or 7 field Quartz Cron expression format (second, minute, hour, day-of-month, month, day-of-week, optional year).
     */
    QUARTZ,

    /**
     * Standard 5 field Unix/Linux Crontab expression format (minute, hour, day-of-month, month, day-of-week).
     */
    UNIX
}
