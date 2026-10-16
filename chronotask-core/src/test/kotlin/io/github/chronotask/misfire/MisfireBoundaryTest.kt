package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import io.github.chronotask.trigger.TriggerService
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class MisfireBoundaryTest {

    @Test
    fun `fixed rate alignment preserves nanosecond boundaries for all policies`() {
        val first = Instant.parse("2026-01-01T00:00:00.000123456Z")
        val boundary = first.plusSeconds(3)
        val service = TriggerService()
        for (offset in listOf(-1L, 0L, 1L)) {
            val now = boundary.plusNanos(offset)
            val latestMissed = if (offset > 0) boundary else first.plusSeconds(2)
            val next = if (offset > 0) first.plusSeconds(4) else boundary
            for (policy in listOf(MisfirePolicy.SKIP, MisfirePolicy.FIRE_ONCE, MisfirePolicy.CatchUp(2))) {
                val result = service.evaluate(
                    schedule = TaskSchedule.FixedRate(1000),
                    misfirePolicy = policy,
                    registeredAt = first,
                    now = now
                )
                assertEquals(next, result.nextScheduledAt, "offset=$offset, policy=$policy")
                val expectedTriggers = when (policy) {
                    is MisfirePolicy.Skip -> emptyList()
                    is MisfirePolicy.FireOnce -> listOf(latestMissed)
                    is MisfirePolicy.CatchUp -> listOf(first, first.plusSeconds(1))
                }
                assertEquals(expectedTriggers, result.triggers.map { it.scheduledAt })
                if (policy !is MisfirePolicy.FireOnce) {
                    assertEquals(latestMissed, result.alignedLastScheduledAt)
                }
            }
        }
    }

    @Test
    fun `scheduledAt strictly before now is MISSED and handled by MisfireResolver`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:00:01Z")
        val schedule = TaskSchedule.FixedRate(10000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        assertEquals(1, result.triggers.size)
        assertEquals(TriggerSource.MISFIRE, result.triggers[0].source)
        assertEquals(registered, result.triggers[0].scheduledAt)
    }

    @Test
    fun `scheduledAt equals now is ON TIME and generated with source SCHEDULE`() {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        val service = TriggerService()
        val schedule = TaskSchedule.FixedRate(5000L)

        val result = service.evaluate(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            registeredAt = now,
            now = now
        )

        assertEquals(1, result.triggers.size)
        assertEquals(TriggerSource.SCHEDULE, result.triggers[0].source)
        assertEquals(now, result.triggers[0].scheduledAt)
    }

    @Test
    fun `scheduledAt after now is FUTURE and generated with source SCHEDULE`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T09:59:59Z")
        val service = TriggerService()
        val schedule = TaskSchedule.FixedRate(5000L)

        val result = service.evaluate(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            registeredAt = registered,
            now = now
        )

        assertEquals(1, result.triggers.size)
        assertEquals(TriggerSource.SCHEDULE, result.triggers[0].source)
        assertEquals(registered, result.triggers[0].scheduledAt)
    }

    @Test
    fun `MisfireContext requires firstScheduledAt strictly before now`() {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = now,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        assertThrows<IllegalArgumentException> {
            MisfireContext(
                schedule = TaskSchedule.FixedRate(1000L),
                misfirePolicy = MisfirePolicy.SKIP,
                triggerContext = triggerCtx,
                firstScheduledAt = now, // equal to now
                now = now
            )
        }
    }
}
