package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ExecutionListenerTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `failing listener callbacks do not corrupt execution result`() = runBlocking {
        val definition = TaskDefinition(
            id = "task-listener-fail",
            name = "Listener Fail Task",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = { /* success */ }
        )

        val execution = TaskExecution(
            taskId = "task-listener-fail",
            trigger = sampleTrigger
        )

        val failingListener = object : ExecutionListener {
            override fun onStarted(def: TaskDefinition, exec: TaskExecution) {
                throw RuntimeException("Listener onStarted exploded")
            }

            override fun onSuccess(def: TaskDefinition, exec: TaskExecution) {
                throw RuntimeException("Listener onSuccess exploded")
            }
        }

        val result = DefaultTaskRunner.run(
            definition = definition,
            execution = execution,
            listener = failingListener
        )

        assertEquals(ExecutionResult.Success, result)
    }
}
