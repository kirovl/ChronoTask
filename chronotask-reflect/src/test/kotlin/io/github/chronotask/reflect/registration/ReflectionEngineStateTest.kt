package io.github.chronotask.reflect.registration

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.EngineStateException
import io.github.chronotask.reflect.registerAnnotated
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReflectionEngineStateTest {

    class SampleService {
        @Scheduled(intervalMs = 1000, id = "state-task")
        fun task() {}
    }

    @Test
    fun `registration succeeds in CREATED, RUNNING and PAUSED states`() {
        val engine = ChronoTask.create()

        // CREATED
        val handles1 = engine.registerAnnotated(object {
            @Scheduled(intervalMs = 1000, id = "task-created")
            fun run() {}
        })
        assertEquals(1, handles1.size)

        // RUNNING
        engine.start()
        val handles2 = engine.registerAnnotated(object {
            @Scheduled(intervalMs = 1000, id = "task-running")
            fun run() {}
        })
        assertEquals(1, handles2.size)

        // PAUSED
        engine.pause()
        val handles3 = engine.registerAnnotated(object {
            @Scheduled(intervalMs = 1000, id = "task-paused")
            fun run() {}
        })
        assertEquals(1, handles3.size)

        // STOPPED
        engine.stop()
        assertThrows(EngineStateException::class.java) {
            engine.registerAnnotated(SampleService())
        }
    }
}
