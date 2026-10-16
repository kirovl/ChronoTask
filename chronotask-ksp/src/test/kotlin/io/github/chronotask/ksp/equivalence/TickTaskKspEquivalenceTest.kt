package io.github.chronotask.ksp.equivalence

import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TickEquivalenceTarget {
    @TickTask(intervalMs = 1000, initialDelayMs = 500, id = "tick-version")
    fun tickTask() {}

    @Scheduled(mode = ScheduleMode.FIXED_RATE, intervalMs = 1000, initialDelayMs = 500, id = "scheduled-version")
    fun scheduledTask() {}
}

class TickTaskKspEquivalenceTest {

    @Test
    fun `TickTask and Scheduled FixedRate generate identical TaskSchedule and policies`() {
        val target = TickEquivalenceTarget()
        val defs = TickEquivalenceTarget_ChronoTaskBinding.definitions(target)

        assertEquals(2, defs.size)
        val tickDef = defs.first { it.id == "tick-version" }
        val scheduledDef = defs.first { it.id == "scheduled-version" }

        assertEquals(tickDef.schedule, scheduledDef.schedule)
        assertEquals(tickDef.concurrencyPolicy, scheduledDef.concurrencyPolicy)
        assertEquals(tickDef.misfirePolicy, scheduledDef.misfirePolicy)
        assertEquals(tickDef.dispatcherKey, scheduledDef.dispatcherKey)
        assertEquals(tickDef.isEnabled, scheduledDef.isEnabled)
    }
}
