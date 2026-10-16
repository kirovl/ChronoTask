package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduledTarget {
    @Scheduled(
        id = "custom-rate-task",
        name = "Custom Rate Task",
        intervalMs = 5000,
        initialDelayMs = 1000,
        mode = ScheduleMode.FIXED_RATE,
        concurrency = AnnotationConcurrency.ALLOW,
        misfire = AnnotationMisfire.FIRE_ONCE
    )
    fun rateTask() {}

    @Scheduled(
        id = "custom-delay-task",
        name = "Custom Delay Task",
        intervalMs = 8000,
        initialDelayMs = 2000,
        mode = ScheduleMode.FIXED_DELAY,
        concurrency = AnnotationConcurrency.QUEUE_ONE,
        misfire = AnnotationMisfire.CATCH_UP,
        catchUpLimit = 50
    )
    fun delayTask() {}
}

class ScheduledGenerationTest {

    @Test
    fun `definitions accurately maps Scheduled FixedRate and FixedDelay properties`() {
        val target = ScheduledTarget()
        val definitions = ScheduledTarget_ChronoTaskBinding.definitions(target)

        assertEquals(2, definitions.size)

        val rateDef = definitions.first { it.id == "custom-rate-task" }
        assertEquals("Custom Rate Task", rateDef.name)
        assertTrue(rateDef.schedule is TaskSchedule.FixedRate)
        val rateSchedule = rateDef.schedule as TaskSchedule.FixedRate
        assertEquals(5000L, rateSchedule.intervalMs)
        assertEquals(1000L, rateSchedule.initialDelayMs)
        assertEquals(ConcurrencyPolicy.Allow, rateDef.concurrencyPolicy)
        assertEquals(MisfirePolicy.FIRE_ONCE, rateDef.misfirePolicy)

        val delayDef = definitions.first { it.id == "custom-delay-task" }
        assertEquals("Custom Delay Task", delayDef.name)
        assertTrue(delayDef.schedule is TaskSchedule.FixedDelay)
        val delaySchedule = delayDef.schedule as TaskSchedule.FixedDelay
        assertEquals(8000L, delaySchedule.delayMs)
        assertEquals(2000L, delaySchedule.initialDelayMs)
        assertEquals(ConcurrencyPolicy.QueueOne, delayDef.concurrencyPolicy)
        assertEquals(MisfirePolicy.CatchUp(50), delayDef.misfirePolicy)
    }
}
