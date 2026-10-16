package io.github.chronotask.reflect.registration

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DuplicateTaskException
import io.github.chronotask.engine.TaskNotFoundException
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.registerAnnotated
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReflectionRollbackTest {

    class BatchService {
        @Scheduled(intervalMs = 1000, id = "task-alpha")
        fun alpha() {}

        @Scheduled(intervalMs = 2000, id = "task-beta")
        fun beta() {}
    }

    @Test
    fun `partial registration failure rolls back previously registered tasks in same batch`() {
        val engine = ChronoTask.create()

        // Pre-register "task-beta" into engine directly
        engine.register(
            TaskDefinition(
                id = "task-beta",
                name = "task-beta",
                schedule = TaskSchedule.Once(0),
                executable = { }
            )
        )

        val service = BatchService()

        // registerAnnotated will scan [task-alpha, task-beta]
        // task-alpha registers, then task-beta throws DuplicateTaskException
        // rollback must unregister task-alpha
        assertThrows(DuplicateTaskException::class.java) {
            engine.registerAnnotated(service)
        }

        // Verify task-alpha is NOT present in engine
        assertNull(engine.getHandle("task-alpha"), "task-alpha should have been rolled back")
    }
}
