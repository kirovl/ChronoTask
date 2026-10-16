package io.github.chronotask.trigger

import io.github.chronotask.clock.ChronoClock
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TriggerServiceTest {

    @Test
    fun `evaluates on-time schedule when now equals plannedInstant`() {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        val fixedClock = ChronoClock { now }
        val service = TriggerService(clock = fixedClock)

        val schedule = TaskSchedule.FixedRate(5000L)
        val result = service.evaluate(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.SKIP,
            registeredAt = now,
            now = now
        )

        assertEquals(1, result.triggers.size)
        val trigger = result.triggers[0]
        assertEquals(now, trigger.scheduledAt)
        assertEquals(now, trigger.discoveredAt)
        assertEquals(TriggerSource.SCHEDULE, trigger.source)
        assertEquals(now, result.nextScheduledAt)
    }

    @Test
    fun `evaluates future schedule when now is before plannedInstant`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val planned = Instant.parse("2026-01-01T10:00:10Z")
        val service = TriggerService(clock = { registered })

        val schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 10000L)
        val result = service.evaluate(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.SKIP,
            registeredAt = registered,
            now = registered
        )

        assertEquals(1, result.triggers.size)
        assertEquals(planned, result.triggers[0].scheduledAt)
        assertEquals(TriggerSource.SCHEDULE, result.triggers[0].source)
        assertEquals(planned, result.nextScheduledAt)
    }

    @Test
    fun `evaluates misfire when plannedInstant is before now`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:05:00Z")
        val service = TriggerService(clock = { now })

        val schedule = TaskSchedule.FixedRate(intervalMs = 60000L)
        val result = service.evaluate(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            registeredAt = registered,
            now = now
        )

        assertEquals(1, result.triggers.size)
        assertEquals(TriggerSource.MISFIRE, result.triggers[0].source)
        assertEquals(Instant.parse("2026-01-01T10:04:00Z"), result.triggers[0].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:05:00Z"), result.nextScheduledAt)
    }

    @Test
    fun `evaluates schedule termination returns empty triggers and null nextScheduledAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val service = TriggerService()

        val schedule = TaskSchedule.Once(5000L)
        val result = service.evaluate(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.SKIP,
            registeredAt = registered,
            lastScheduledAt = Instant.parse("2026-01-01T10:00:05Z")
        )

        assertTrue(result.triggers.isEmpty())
        assertNull(result.nextScheduledAt)
    }
}
