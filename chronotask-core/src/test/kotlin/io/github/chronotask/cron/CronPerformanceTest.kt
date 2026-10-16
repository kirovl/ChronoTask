package io.github.chronotask.cron

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.system.measureTimeMillis

class CronPerformanceTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `sparse leap year search completes quickly via field jumping`() {
        val schedule = TaskSchedule.Cron("0 0 0 29 2 ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val reg = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, utc).toInstant()
        var lastScheduled: Instant? = reg
        val elapsed = measureTimeMillis {
            for (i in 0 until 10) {
                val context = TriggerContext(
                    now = lastScheduled ?: reg,
                    registeredAt = reg,
                    lastScheduledAt = lastScheduled,
                    lastStartedAt = null,
                    lastCompletedAt = null
                )
                lastScheduled = calc.nextTrigger(context)
                assertTrue(lastScheduled != null)
            }
        }

        // 10 leap years forward should take < 100ms
        assertTrue(elapsed < 100, "Expected sparse search to take < 100ms but took ${elapsed}ms")
    }

    @Test
    fun `impossible date returns null quickly without infinite loop`() {
        // February 31 does not exist in any year
        val schedule = TaskSchedule.Cron("0 0 0 31 2 ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val reg = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, utc).toInstant()
        val context = TriggerContext(
            now = reg,
            registeredAt = reg,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        var result: Instant? = null
        val elapsed = measureTimeMillis {
            result = calc.nextTrigger(context)
        }

        assertNull(result)
        assertTrue(elapsed < 100, "Expected impossible date search to take < 100ms but took ${elapsed}ms")
    }

    @Test
    fun `high frequency calculation throughput`() {
        val schedule = TaskSchedule.Cron("*/5 * * * * ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val reg = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, utc).toInstant()
        var lastScheduled: Instant? = reg
        val count = 2000

        val elapsed = measureTimeMillis {
            for (i in 0 until count) {
                val context = TriggerContext(
                    now = lastScheduled ?: reg,
                    registeredAt = reg,
                    lastScheduledAt = lastScheduled,
                    lastStartedAt = null,
                    lastCompletedAt = null
                )
                lastScheduled = calc.nextTrigger(context)
            }
        }

        assertTrue(elapsed < 200, "Expected 2000 calculations to take < 200ms but took ${elapsed}ms")
    }
}
