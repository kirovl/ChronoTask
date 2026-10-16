package io.github.chronotask.cron

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.BitSet

/**
 * Immutable compiled representation of a Cron expression.
 *
 * @property expression The original cron expression string.
 * @property dialect The dialect used to parse this expression.
 * @property seconds BitSet containing valid seconds (0..59).
 * @property minutes BitSet containing valid minutes (0..59).
 * @property hours BitSet containing valid hours (0..23).
 * @property daysOfMonth BitSet containing valid days of month (1..31).
 * @property months BitSet containing valid months (1..12).
 * @property daysOfWeek BitSet containing valid days of week (1..7, where 1=Sunday, 2=Monday, ..., 7=Saturday).
 * @property years BitSet containing valid years (1970..2199).
 * @property isDomWildcard True if day-of-month starts with '*', including star-based steps in UNIX.
 * @property isDowWildcard True if day-of-week starts with '*', including star-based steps in UNIX.
 * @property isDomQuestion True if day-of-month was specified as '?'.
 * @property isDowQuestion True if day-of-week was specified as '?'.
 */
data class CronExpression(
    val expression: String,
    val dialect: CronDialect,
    val seconds: BitSet,
    val minutes: BitSet,
    val hours: BitSet,
    val daysOfMonth: BitSet,
    val months: BitSet,
    val daysOfWeek: BitSet,
    val years: BitSet,
    val isDomWildcard: Boolean = false,
    val isDowWildcard: Boolean = false,
    val isDomQuestion: Boolean = false,
    val isDowQuestion: Boolean = false
) {

    /**
     * Evaluates whether the given day of month and day of week match the expression rules
     * considering the dialect and calendar month length.
     *
     * @param dayOfMonth Day of month (1..31).
     * @param quartzDow Standardized day of week (1..7 where 1=Sunday, 7=Saturday).
     * @param daysInMonth Actual number of days in the current month (28..31).
     */
    fun matchesDay(dayOfMonth: Int, quartzDow: Int, daysInMonth: Int): Boolean {
        if (dayOfMonth > daysInMonth) return false

        return when (dialect) {
            CronDialect.QUARTZ -> {
                if (isDomQuestion) {
                    daysOfWeek.get(quartzDow)
                } else if (isDowQuestion) {
                    daysOfMonth.get(dayOfMonth)
                } else {
                    daysOfMonth.get(dayOfMonth) && daysOfWeek.get(quartzDow)
                }
            }
            CronDialect.UNIX -> {
                if (!isDomWildcard && !isDowWildcard) {
                    daysOfMonth.get(dayOfMonth) || daysOfWeek.get(quartzDow)
                } else {
                    daysOfMonth.get(dayOfMonth) && daysOfWeek.get(quartzDow)
                }
            }
        }
    }

    /**
     * Checks if the given [ZonedDateTime] matches all fields of this Cron expression.
     */
    fun matches(zdt: ZonedDateTime): Boolean {
        val year = zdt.year
        if (year < 1970 || year > 2199 || !years.get(year)) return false

        val month = zdt.monthValue
        if (!months.get(month)) return false

        val lengthOfMonth = zdt.toLocalDate().lengthOfMonth()
        val dayOfMonth = zdt.dayOfMonth
        val quartzDow = if (zdt.dayOfWeek == DayOfWeek.SUNDAY) 1 else zdt.dayOfWeek.value + 1
        if (!matchesDay(dayOfMonth, quartzDow, lengthOfMonth)) return false

        val hour = zdt.hour
        if (!hours.get(hour)) return false

        val minute = zdt.minute
        if (!minutes.get(minute)) return false

        val second = zdt.second
        if (!seconds.get(second)) return false

        return true
    }
}
