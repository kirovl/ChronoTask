package io.github.chronotask.reflect.invocation

import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionMethodAdapter
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ReflectionCancellationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    class CancellableService {
        val started = CompletableDeferred<Unit>()
        var cleanedUp = false

        suspend fun cancellableTask() {
            try {
                started.complete(Unit)
                delay(100_000)
            } finally {
                cleanedUp = true
            }
        }
    }

    @Test
    fun `cancelled suspend function runs finally block and notifies cancellation listener`() = runBlocking {
        val service = CancellableService()
        val method = service.javaClass.methods.first { it.name == "cancellableTask" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "cancellable-task",
            name = "Cancellable Task",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        var cancelledNotified = false
        val listener = object : ExecutionListener {
            override fun onCancelled(task: TaskDefinition, execution: TaskExecution) {
                cancelledNotified = true
            }
        }

        val execution = TaskExecution(
            executionId = ExecutionId.generate(),
            taskId = "cancellable-task",
            trigger = ScheduledTrigger(scheduledAt = now, discoveredAt = now)
        )

        val job = launch(Dispatchers.Default) {
            DefaultTaskRunner.run(taskDef, execution, listener = listener)
        }

        service.started.await()
        job.cancel()
        job.join()

        assertTrue(service.cleanedUp, "Finally block in suspend function must execute on cancellation")
        assertTrue(cancelledNotified, "Cancellation must be reported to ExecutionListener")
    }
}
