package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.system.measureTimeMillis

class MisfireAntiExplosionTest {

    @Test
    fun `30-day offline jump with 1-second interval resolves in O(1) mathematical time`() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val now = registered.plus(Duration.ofDays(30)) // 30 days = 2,592,000 seconds
        val schedule = TaskSchedule.FixedRate(intervalMs = 1000L) // 1 second

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(maxExecutions = 100),
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        var result: MisfireResult
        val durationMs = measureTimeMillis {
            result = DefaultMisfireResolver.resolve(misfireCtx)
        }

        // Bounded by maxExecutions = 100
        assertEquals(100, result.triggers.size)
        assertEquals(registered, result.triggers[0].scheduledAt)
        assertEquals(registered.plusMillis(99000L), result.triggers[99].scheduledAt)

        // Advanced to next future point
        assertEquals(now, result.nextScheduledAt)

        // Must complete instantaneously without 2,592,000 iterations
        assertTrue(durationMs < 50, "Misfire resolution took $durationMs ms, expected < 50ms")
    }

    @Test
    fun `30-day offline jump with Skip resolves in O(1) mathematical time`() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val now = registered.plus(Duration.ofDays(30))
        val schedule = TaskSchedule.FixedRate(intervalMs = 1000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.SKIP,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        var result: MisfireResult
        val durationMs = measureTimeMillis {
            result = DefaultMisfireResolver.resolve(misfireCtx)
        }

        assertTrue(result.triggers.isEmpty())
        assertEquals(now, result.nextScheduledAt)
        assertTrue(durationMs < 50, "Skip resolution took $durationMs ms, expected < 50ms")
    }
}
