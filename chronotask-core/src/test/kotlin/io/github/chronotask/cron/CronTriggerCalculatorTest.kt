package io.github.chronotask.cron

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CronTriggerCalculatorTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `calculates next trigger for standard quartz cron`() {
        val schedule = TaskSchedule.Cron("0 0 12 * * ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val reg = ZonedDateTime.of(2024, 9, 18, 10, 0, 0, 0, utc).toInstant()
        val context = TriggerContext(
            now = reg,
            registeredAt = reg,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val next = calc.nextTrigger(context)
        val expected = ZonedDateTime.of(2024, 9, 18, 12, 0, 0, 0, utc).toInstant()
        assertEquals(expected, next)
    }

    @Test
    fun `strict exclusive boundary prevents duplicate trigger at exact matching instant`() {
        val schedule = TaskSchedule.Cron("0 0 12 * * ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val noonToday = ZonedDateTime.of(2024, 9, 18, 12, 0, 0, 0, utc).toInstant()
        val context = TriggerContext(
            now = noonToday.plusMillis(100),
            registeredAt = noonToday.minusSeconds(3600),
            lastScheduledAt = noonToday,
            lastStartedAt = noonToday,
            lastCompletedAt = noonToday.plusMillis(100)
        )

        val next = calc.nextTrigger(context)
        val noonTomorrow = ZonedDateTime.of(2024, 9, 19, 12, 0, 0, 0, utc).toInstant()
        assertEquals(noonTomorrow, next)
    }

    @Test
    fun `sub-second base instant finds matching second within the current minute`() {
        val schedule = TaskSchedule.Cron("0 0 12 * * ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        // 11:59:59.999 -> next is 12:00:00.000 today
        val subSecondBase = ZonedDateTime.of(2024, 9, 18, 11, 59, 59, 999_000_000, utc).toInstant()
        val context = TriggerContext(
            now = subSecondBase,
            registeredAt = subSecondBase,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val next = calc.nextTrigger(context)
        val expected = ZonedDateTime.of(2024, 9, 18, 12, 0, 0, 0, utc).toInstant()
        assertEquals(expected, next)
    }

    @Test
    fun `handles leap year correctly for February 29`() {
        val schedule = TaskSchedule.Cron("0 0 0 29 2 ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        // 2024 is a leap year
        val jan2024 = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, utc).toInstant()
        var context = TriggerContext(jan2024, jan2024, null, null, null)

        val feb29_2024 = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2024, 2, 29, 0, 0, 0, 0, utc).toInstant(), feb29_2024)

        // Next after 2024-02-29 should be 2028-02-29 (2025, 2026, 2027 skipped)
        context = TriggerContext(feb29_2024!!, jan2024, feb29_2024, feb29_2024, feb29_2024)
        val feb29_2028 = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2028, 2, 29, 0, 0, 0, 0, utc).toInstant(), feb29_2028)
    }

    @Test
    fun `handles month rollover from 30-day month to next month`() {
        val schedule = TaskSchedule.Cron("0 0 12 31 * ?", utc, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        // Start in April (has 30 days) -> Next 31st is May 31
        val apr1 = ZonedDateTime.of(2024, 4, 1, 0, 0, 0, 0, utc).toInstant()
        val context = TriggerContext(apr1, apr1, null, null, null)

        val may31 = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2024, 5, 31, 12, 0, 0, 0, utc).toInstant(), may31)
    }

    @Test
    fun `handles DST spring-forward gap by skipping missing local hour`() {
        val nyZone = ZoneId.of("America/New_York")
        // In NY, 2024-03-10 02:00 -> 03:00 (02:30 does not exist locally)
        val schedule = TaskSchedule.Cron("0 30 2 * * ?", nyZone, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val mar9 = ZonedDateTime.of(2024, 3, 9, 0, 0, 0, 0, nyZone).toInstant()
        var context = TriggerContext(mar9, mar9, null, null, null)

        val mar9_0230 = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2024, 3, 9, 2, 30, 0, 0, nyZone).toInstant(), mar9_0230)

        // From mar9_0230, next day 02:30 is missing, so it should skip to March 11 02:30
        context = TriggerContext(mar9_0230!!, mar9, mar9_0230, null, null)
        val mar11_0230 = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2024, 3, 11, 2, 30, 0, 0, nyZone).toInstant(), mar11_0230)
    }

    @Test
    fun `handles DST fall-back overlap by triggering both occurrences monotonically`() {
        val nyZone = ZoneId.of("America/New_York")
        // In NY, 2024-11-03 01:00:00 to 01:59:59 repeats (EDT then EST)
        val schedule = TaskSchedule.Cron("0 30 1 * * ?", nyZone, CronDialect.QUARTZ)
        val calc = CronTriggerCalculator(schedule)

        val nov2 = ZonedDateTime.of(2024, 11, 2, 0, 0, 0, 0, nyZone).toInstant()
        var context = TriggerContext(nov2, nov2, null, null, null)

        val nov2_trigger = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2024, 11, 2, 1, 30, 0, 0, nyZone).toInstant(), nov2_trigger)

        // First occurrence on Nov 3 (EDT, offset -04:00)
        context = TriggerContext(nov2_trigger!!, nov2, nov2_trigger, null, null)
        val nov3_first = calc.nextTrigger(context)
        assertNotNull(nov3_first)

        val firstZdt = ZonedDateTime.ofInstant(nov3_first, nyZone)
        assertEquals(1, firstZdt.hour)
        assertEquals(30, firstZdt.minute)

        // Second occurrence on Nov 3 (EST, offset -05:00)
        context = TriggerContext(nov3_first!!, nov2, nov3_first, null, null)
        val nov3_second = calc.nextTrigger(context)
        assertNotNull(nov3_second)

        assertTrue(nov3_second!! > nov3_first)
        val secondZdt = ZonedDateTime.ofInstant(nov3_second, nyZone)
        assertEquals(1, secondZdt.hour)
        assertEquals(30, secondZdt.minute)

        // Nov 4 occurrence
        context = TriggerContext(nov3_second!!, nov2, nov3_second, null, null)
        val nov4_trigger = calc.nextTrigger(context)
        assertEquals(ZonedDateTime.of(2024, 11, 4, 1, 30, 0, 0, nyZone).toInstant(), nov4_trigger)
    }
}
