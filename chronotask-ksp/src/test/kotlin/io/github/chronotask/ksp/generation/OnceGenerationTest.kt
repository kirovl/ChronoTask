package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnceTarget {
    @Once(delayMs = 3000, id = "delayed-init-task")
    fun initTask() {}

    @Once(id = "immediate-once-task")
    fun immediateTask() {}
}

class OnceGenerationTest {

    @Test
    fun `definitions accurately maps Once properties with delay and immediate`() {
        val target = OnceTarget()
        val definitions = OnceTarget_ChronoTaskBinding.definitions(target)

        assertEquals(2, definitions.size)

        val delayedDef = definitions.first { it.id == "delayed-init-task" }
        assertTrue(delayedDef.schedule is TaskSchedule.Once)
        val delayedSchedule = delayedDef.schedule as TaskSchedule.Once
        assertEquals(3000L, delayedSchedule.delayMs)

        val immDef = definitions.first { it.id == "immediate-once-task" }
        assertTrue(immDef.schedule is TaskSchedule.Once)
        val immSchedule = immDef.schedule as TaskSchedule.Once
        assertEquals(0L, immSchedule.delayMs)
    }
}
