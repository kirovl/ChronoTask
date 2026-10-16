package io.github.chronotask.execution

import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class TaskExecutionTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `default constructor generates unique executionId`() {
        val exec1 = TaskExecution(taskId = "task-1", trigger = sampleTrigger)
        val exec2 = TaskExecution(taskId = "task-1", trigger = sampleTrigger)

        assertNotNull(exec1.executionId)
        assertNotNull(exec2.executionId)
        assertNotEquals(exec1.executionId, exec2.executionId)
        assertEquals("task-1", exec1.taskId)
        assertEquals(1, exec1.attempt)
    }

    @Test
    fun `blank taskId throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            TaskExecution(taskId = "   ", trigger = sampleTrigger)
        }
    }

    @Test
    fun `attempt zero or negative throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            TaskExecution(taskId = "task-1", trigger = sampleTrigger, attempt = 0)
        }
        assertThrows<IllegalArgumentException> {
            TaskExecution(taskId = "task-1", trigger = sampleTrigger, attempt = -1)
        }
    }

    @Test
    fun `ExecutionId value validation`() {
        val id = ExecutionId.of("custom-id-123")
        assertEquals("custom-id-123", id.value)
        assertEquals("custom-id-123", id.toString())

        assertThrows<IllegalArgumentException> {
            ExecutionId("")
        }
    }
}
