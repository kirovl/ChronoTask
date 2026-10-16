package io.github.chronotask.model

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.trigger.TriggerCalculator
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Specification of when and how often a task should be scheduled.
 */
sealed interface TaskSchedule {

    /**
     * Schedules a task at fixed rate intervals based on the planned schedule timeline (anti-drift).
     *
     * @property intervalMs Interval between trigger points in milliseconds. Must be > 0.
     * @property initialDelayMs Initial delay before the first trigger point in milliseconds. Must be >= 0.
     */
    data class FixedRate @JvmOverloads constructor(
        val intervalMs: Long,
        val initialDelayMs: Long = 0L
    ) : TaskSchedule {
        init {
            require(intervalMs > 0) { "intervalMs must be positive: $intervalMs" }
            require(initialDelayMs >= 0) { "initialDelayMs must be non-negative: $initialDelayMs" }
        }

        @JvmOverloads
        constructor(
            interval: Long,
            unit: TimeUnit,
            initialDelay: Long = 0L,
            initialDelayUnit: TimeUnit = TimeUnit.MILLISECONDS
        ) : this(unit.toMillis(interval), toNonNegativeMillis(initialDelay, initialDelayUnit, "initialDelay"))
    }

    /**
     * Schedules a task with a fixed delay between the completion of one execution and the start of the next.
     *
     * @property delayMs Delay between execution completion and next trigger in milliseconds. Must be > 0.
     * @property initialDelayMs Initial delay before the first trigger point in milliseconds. Must be >= 0.
     */
    data class FixedDelay @JvmOverloads constructor(
        val delayMs: Long,
        val initialDelayMs: Long = 0L
    ) : TaskSchedule {
        init {
            require(delayMs > 0) { "delayMs must be positive: $delayMs" }
            require(initialDelayMs >= 0) { "initialDelayMs must be non-negative: $initialDelayMs" }
        }

        @JvmOverloads
        constructor(
            delay: Long,
            unit: TimeUnit,
            initialDelay: Long = 0L,
            initialDelayUnit: TimeUnit = TimeUnit.MILLISECONDS
        ) : this(unit.toMillis(delay), toNonNegativeMillis(initialDelay, initialDelayUnit, "initialDelay"))
    }

    /**
     * Schedules a task matching a Cron expression in the specified time zone.
     *
     * @property expression Cron expression string. Must not be blank.
     * @property zoneId Target time zone for resolving cron field matches.
     * @property dialect The Cron syntax dialect ([CronDialect.QUARTZ] or [CronDialect.UNIX]).
     */
    data class Cron @JvmOverloads constructor(
        val expression: String,
        val zoneId: ZoneId,
        val dialect: CronDialect = CronDialect.QUARTZ
    ) : TaskSchedule {
        init {
            require(expression.isNotBlank()) { "Cron expression must not be blank" }
        }
    }

    /**
     * Schedules a task to execute exactly once after a specified delay.
     *
     * @property delayMs Delay before triggering in milliseconds. Must be >= 0 (0 means immediate trigger upon start).
     */
    data class Once(
        val delayMs: Long = 0L
    ) : TaskSchedule {
        init {
            require(delayMs >= 0) { "delayMs must be non-negative: $delayMs" }
        }

        constructor(delay: Long, unit: TimeUnit) : this(toNonNegativeMillis(delay, unit, "delay"))
    }

    /**
     * Custom programmatic schedule evaluated by a [TriggerCalculator].
     *
     * @property calculator The SPI calculator producing next trigger timestamps.
     */
    data class Custom(
        val calculator: TriggerCalculator
    ) : TaskSchedule
}

private fun toNonNegativeMillis(value: Long, unit: TimeUnit, fieldName: String): Long {
    require(value >= 0) { "$fieldName must be non-negative: $value" }
    return unit.toMillis(value)
}
