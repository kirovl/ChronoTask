package io.github.chronotask.engine

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.model.TaskState
import io.github.chronotask.trigger.UnsupportedScheduleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.ZoneId

class TaskRegistrationTest {

    @Test
    fun `registering in CREATED, RUNNING and PAUSED states succeeds`() {
        val engine = ChronoTaskEngine.create()

        val def1 = TaskDefinition("task-created", "Created Task", TaskSchedule.FixedRate(1000L), executable = {})
        val handle1 = engine.register(def1)
        assertEquals(TaskState.REGISTERED, handle1.state)

        engine.start()
        val def2 = TaskDefinition("task-running", "Running Task", TaskSchedule.FixedRate(1000L), executable = {})
        val handle2 = engine.register(def2)
        assertEquals(TaskState.SCHEDULED, handle2.state)

        engine.pause()
        val def3 = TaskDefinition("task-paused", "Paused Task", TaskSchedule.FixedRate(1000L), executable = {})
        val handle3 = engine.register(def3)
        assertEquals(TaskState.PAUSED, handle3.state)

        engine.stop()
    }

    @Test
    fun `duplicate task registration throws DuplicateTaskException`() {
        val engine = ChronoTaskEngine.create()
        val def = TaskDefinition("task-unique", "Unique Task", TaskSchedule.FixedRate(1000L), executable = {})

        engine.register(def)
        assertThrows(DuplicateTaskException::class.java) {
            engine.register(def)
        }
        engine.stop()
    }

    @Test
    fun `unknown dispatcherKey fails fast during registration`() {
        val engine = ChronoTaskEngine.create()
        val def = TaskDefinition(
            id = "task-bad-dispatcher",
            name = "Bad Dispatcher Task",
            schedule = TaskSchedule.FixedRate(1000L),
            dispatcherKey = "non-existent-pool",
            executable = {}
        )

        assertThrows(IllegalArgumentException::class.java) {
            engine.register(def)
        }
        engine.stop()
    }

    @Test
    fun `Cron schedule registration succeeds with valid expression and fails on invalid expression`() {
        val engine = ChronoTaskEngine.create()
        val def = TaskDefinition(
            id = "cron-task",
            name = "Cron Task",
            schedule = TaskSchedule.Cron("0 0 * * * ?", ZoneId.of("UTC"), CronDialect.QUARTZ),
            executable = {}
        )

        val handle = engine.register(def)
        assertEquals(TaskState.REGISTERED, handle.state)

        val invalidDef = TaskDefinition(
            id = "invalid-cron-task",
            name = "Invalid Cron Task",
            schedule = TaskSchedule.Cron("invalid cron", ZoneId.of("UTC"), CronDialect.QUARTZ),
            executable = {}
        )
        assertThrows(io.github.chronotask.cron.CronParseException::class.java) {
            engine.register(invalidDef)
        }
        engine.stop()
    }

    @Test
    fun `task with isEnabled false is initially PAUSED`() {
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition(
            id = "disabled-task",
            name = "Disabled Task",
            schedule = TaskSchedule.FixedRate(1000L),
            isEnabled = false,
            executable = {}
        )

        val handle = engine.register(def)
        assertEquals(TaskState.PAUSED, handle.state)
        engine.stop()
    }
}
