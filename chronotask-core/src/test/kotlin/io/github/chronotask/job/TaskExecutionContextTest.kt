package io.github.chronotask.job

import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class TaskExecutionContextTest {

    @Test
    fun `test TaskExecutionContext validation`() {
        val trigger = ScheduledTrigger(Instant.now(), Instant.now(), TriggerSource.SCHEDULE)

        assertThrows<IllegalArgumentException> {
            TaskExecutionContext(taskId = "", trigger = trigger)
        }
        assertThrows<IllegalArgumentException> {
            TaskExecutionContext(taskId = "   ", trigger = trigger)
        }
        assertThrows<IllegalArgumentException> {
            TaskExecutionContext(taskId = "task-1", trigger = trigger, attempt = 0)
        }
        assertThrows<IllegalArgumentException> {
            TaskExecutionContext(taskId = "task-1", trigger = trigger, attempt = -1)
        }
    }

    @Test
    fun `test TaskExecutionContext values and defaults`() {
        val trigger = ScheduledTrigger(Instant.now(), Instant.now(), TriggerSource.SCHEDULE)
        val ctx = TaskExecutionContext("task-order-sync", trigger)

        assertEquals("task-order-sync", ctx.taskId)
        assertEquals(trigger, ctx.trigger)
        assertEquals(1, ctx.attempt)

        val ctx2 = TaskExecutionContext("task-order-sync", trigger, attempt = 3)
        assertEquals(3, ctx2.attempt)

        assertEquals(ctx, TaskExecutionContext("task-order-sync", trigger, 1))
    }
}
