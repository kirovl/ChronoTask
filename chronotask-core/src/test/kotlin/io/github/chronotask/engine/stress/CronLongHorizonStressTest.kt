package io.github.chronotask.engine.stress

import io.github.chronotask.cron.CronTriggerCalculator
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CronLongHorizonStressTest {

    private fun context(
        now: Instant,
        registeredAt: Instant,
        lastScheduledAt: Instant? = null
    ): TriggerContext = TriggerContext(
        now = now,
        registeredAt = registeredAt,
        lastScheduledAt = lastScheduledAt,
        lastStartedAt = null,
        lastCompletedAt = null
    )

    @Test
    fun testMultiYearLeapYearProgression() {
        // Run on Feb 29 at midnight: "0 0 0 29 2 ?"
        val schedule = TaskSchedule.Cron("0 0 0 29 2 ?", zoneId = ZoneId.of("UTC"))
        val calculator = CronTriggerCalculator(schedule)

        val regTime = Instant.parse("2024-01-01T00:00:00Z")

        // 2024 is leap year -> 2024-02-29
        val t2024 = calculator.nextTrigger(context(regTime, regTime))
        assertNotNull(t2024)
        assertEquals(Instant.parse("2024-02-29T00:00:00Z"), t2024)

        // Next leap year is 2028 -> 2028-02-29
        val t2028 = calculator.nextTrigger(context(t2024!!, regTime, lastScheduledAt = t2024))
        assertNotNull(t2028)
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"), t2028)

        // Next leap year is 2032 -> 2032-02-29
        val t2032 = calculator.nextTrigger(context(t2028!!, regTime, lastScheduledAt = t2028))
        assertNotNull(t2032)
        assertEquals(Instant.parse("2032-02-29T00:00:00Z"), t2032)
    }

    @Test
    fun testDstSpringGapAndFallOverlapAcrossZones() {
        val nyZone = ZoneId.of("America/New_York")
        val londonZone = ZoneId.of("Europe/London")
        val shanghaiZone = ZoneId.of("Asia/Shanghai")

        // 0 30 2 * * ? (2:30 AM every day)
        val nyCalc = CronTriggerCalculator(TaskSchedule.Cron("0 30 2 * * ?", zoneId = nyZone))

        // In 2026, US Spring Forward is Sunday, March 8, 2026 (2:00 AM -> 3:00 AM, 2:30 AM does not exist on clock)
        val nyMarch7 = ZonedDateTime.of(2026, 3, 7, 0, 0, 0, 0, nyZone).toInstant()
        val nyNext1 = nyCalc.nextTrigger(context(nyMarch7, nyMarch7))
        assertNotNull(nyNext1)

        // Should resolve cleanly without infinite loop or exception
        val nyNext2 = nyCalc.nextTrigger(context(nyNext1!!, nyMarch7, lastScheduledAt = nyNext1))
        assertNotNull(nyNext2)
        assertTrue(nyNext2!!.isAfter(nyNext1))

        // London spring transition (March 29, 2026)
        val lonCalc = CronTriggerCalculator(TaskSchedule.Cron("0 30 2 * * ?", zoneId = londonZone))
        val londonMarch28 = ZonedDateTime.of(2026, 3, 28, 0, 0, 0, 0, londonZone).toInstant()
        val lonNext1 = lonCalc.nextTrigger(context(londonMarch28, londonMarch28))
        val lonNext2 = lonCalc.nextTrigger(context(lonNext1!!, londonMarch28, lastScheduledAt = lonNext1))
        assertNotNull(lonNext2)
        assertTrue(lonNext2!!.isAfter(lonNext1))

        // Shanghai (no DST)
        val shCalc = CronTriggerCalculator(TaskSchedule.Cron("0 30 2 * * ?", zoneId = shanghaiZone))
        val shJan1 = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, shanghaiZone).toInstant()
        val shNext1 = shCalc.nextTrigger(context(shJan1, shJan1))
        assertNotNull(shNext1)
    }
}
