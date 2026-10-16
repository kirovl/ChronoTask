package io.github.chronotask.model

import io.github.chronotask.job.TaskExecutable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TaskDefinitionTest {

    @Test
    fun `test TaskDefinition blank id throws exception`() {
        assertThrows<IllegalArgumentException> {
            TaskDefinition(
                id = "",
                schedule = TaskSchedule.Once(0),
                executable = TaskExecutable { }
            )
        }
        assertThrows<IllegalArgumentException> {
            TaskDefinition(
                id = "   ",
                schedule = TaskSchedule.Once(0),
                executable = TaskExecutable { }
            )
        }
    }

    @Test
    fun `test TaskDefinition blank name throws exception`() {
        assertThrows<IllegalArgumentException> {
            TaskDefinition(
                id = "task-1",
                name = "",
                schedule = TaskSchedule.Once(0),
                executable = TaskExecutable { }
            )
        }
        assertThrows<IllegalArgumentException> {
            TaskDefinition(
                id = "task-1",
                name = "   ",
                schedule = TaskSchedule.Once(0),
                executable = TaskExecutable { }
            )
        }
    }

    @Test
    fun `test TaskDefinition default values`() {
        val exec = TaskExecutable { }
        val def = TaskDefinition(
            id = "task-123",
            schedule = TaskSchedule.FixedRate(5000),
            executable = exec
        )

        assertEquals("task-123", def.id)
        assertEquals("task-123", def.name)
        assertEquals(ConcurrencyPolicy.SkipIfRunning, def.concurrencyPolicy)
        assertEquals(MisfirePolicy.Skip, def.misfirePolicy)
        assertNull(def.dispatcherKey)
        assertTrue(def.isEnabled)
        assertSame(exec, def.executable)
    }

    @Test
    fun `test TaskDefinition equality and immutability`() {
        val exec = TaskExecutable { }
        val def1 = TaskDefinition("task-1", "Task One", TaskSchedule.Once(100), executable = exec)
        val def2 = TaskDefinition("task-1", "Task One", TaskSchedule.Once(100), executable = exec)

        assertEquals(def1, def2)
        assertEquals(def1.hashCode(), def2.hashCode())
    }
}
