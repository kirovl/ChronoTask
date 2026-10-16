package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.TickTask
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TickTarget {
    @TickTask(intervalMs = 100, initialDelayMs = 20, id = "fast-tick")
    fun tick() {}
}

class TickTaskGenerationTest {

    @Test
    fun `TickTask maps to FixedRate TaskSchedule`() {
        val target = TickTarget()
        val definitions = TickTarget_ChronoTaskBinding.definitions(target)

        assertEquals(1, definitions.size)
        val def = definitions.single()
        assertEquals("fast-tick", def.id)
        assertTrue(def.schedule is TaskSchedule.FixedRate)
        val schedule = def.schedule as TaskSchedule.FixedRate
        assertEquals(100L, schedule.intervalMs)
        assertEquals(20L, schedule.initialDelayMs)
    }
}
