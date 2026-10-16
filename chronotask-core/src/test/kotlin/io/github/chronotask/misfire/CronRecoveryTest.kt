package io.github.chronotask.misfire

import io.github.chronotask.cron.CronTriggerCalculator
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class CronRecoveryTest {
    private val policies = listOf(MisfirePolicy.Skip, MisfirePolicy.FireOnce, MisfirePolicy.CatchUp(3))

    private fun resolve(schedule: TaskSchedule.Cron, registered: Instant, now: Instant, policy: MisfirePolicy): MisfireResult {
        val context = TriggerContext(now, registered, null, null, null)
        val first = CronTriggerCalculator(schedule).nextTrigger(context)!!
        return DefaultMisfireResolver.resolve(MisfireContext(schedule, policy, context, first, now))
    }

    @Test
    fun `year-long outage produces only bounded compensation and aligns with the next second`() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val boundary = Instant.parse("2027-01-01T00:00:00Z")
        val schedule = TaskSchedule.Cron("* * * * * ?", ZoneId.of("UTC"))
        for (now in listOf(boundary, boundary.plusMillis(500))) {
            for (policy in policies) {
                val result = resolve(schedule, registered, now, policy)
                val lastMissed = if (now == boundary) boundary.minusSeconds(1) else boundary
                assertEquals(lastMissed, result.alignedLastScheduledAt)
                assertEquals(if (now == boundary) boundary else boundary.plusSeconds(1), result.nextScheduledAt)
                val expected = when (policy) {
                    is MisfirePolicy.Skip -> emptyList()
                    is MisfirePolicy.FireOnce -> listOf(lastMissed)
                    is MisfirePolicy.CatchUp -> (1L..3L).map(registered::plusSeconds)
                }
                assertEquals(expected, result.triggers.map { it.scheduledAt })
            }
        }
    }

    @Test
    fun `one nanosecond overdue retains the precise missed trigger`() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val schedule = TaskSchedule.Cron("* * * * * ?", ZoneId.of("UTC"))
        val first = registered.plusSeconds(1)
        val result = resolve(schedule, registered, first.plusNanos(1), MisfirePolicy.FireOnce)
        assertEquals(listOf(first), result.triggers.map { it.scheduledAt })
        assertEquals(first.plusSeconds(1), result.nextScheduledAt)
    }

    @Test
    fun `finite year schedule preserves compensation after its final trigger`() {
        val registered = Instant.parse("2025-12-31T00:00:00Z")
        val schedule = TaskSchedule.Cron("0 0 0 1 1 ? 2026", ZoneId.of("UTC"))
        val now = Instant.parse("2027-01-01T00:00:00Z")
        for (policy in policies) {
            val result = resolve(schedule, registered, now, policy)
            assertNull(result.nextScheduledAt)
            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.alignedLastScheduledAt)
            assertEquals(if (policy == MisfirePolicy.Skip) 0 else 1, result.triggers.size)
        }
    }

    @Test
    fun `DST gap and overlap recovery agree with chronological enumeration`() {
        val zone = ZoneId.of("America/New_York")
        val cases = listOf(
            Triple("0 30 2 * * ?", "2026-03-06T00:00:00Z", "2026-03-10T00:00:00Z"),
            Triple("0 30 1 * * ?", "2026-10-30T00:00:00Z", "2026-11-03T00:00:00Z")
        )
        for ((expression, registeredText, nowText) in cases) {
            val registered = Instant.parse(registeredText)
            val now = Instant.parse(nowText)
            val schedule = TaskSchedule.Cron(expression, zone)
            val calculator = CronTriggerCalculator(schedule)
            val context = TriggerContext(now, registered, null, null, null)
            val first = calculator.nextTrigger(context)!!
            for (policy in policies) {
                val reference = DefaultMisfireResolver.resolve(
                    MisfireContext(TaskSchedule.Custom(calculator), policy, context, first, now)
                )
                val actual = resolve(schedule, registered, now, policy)
                assertEquals(reference.triggers, actual.triggers)
                assertEquals(reference.nextScheduledAt, actual.nextScheduledAt)
            }
        }
    }
}
