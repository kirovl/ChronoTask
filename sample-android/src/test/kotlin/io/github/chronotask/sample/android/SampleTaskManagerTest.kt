package io.github.chronotask.sample.android

import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskState
import io.github.chronotask.sample.android.model.EventType
import io.github.chronotask.sample.android.model.SampleTaskManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SampleTaskManagerTest {

    @Test
    fun `test manager lifecycle transitions`() {
        val manager = SampleTaskManager()
        assertEquals(EngineState.CREATED, manager.engineState.value)
        assertEquals(12, manager.taskList.value.size, "Should register all 12 multi-paradigm tasks")

        // Start
        manager.start()
        assertEquals(EngineState.RUNNING, manager.engineState.value)

        // Pause
        manager.pause()
        assertEquals(EngineState.PAUSED, manager.engineState.value)

        // Resume
        manager.resume()
        assertEquals(EngineState.RUNNING, manager.engineState.value)

        // Stop (Terminal)
        manager.stop()
        assertEquals(EngineState.STOPPED, manager.engineState.value)

        // Recreate
        manager.initEngine()
        assertEquals(EngineState.CREATED, manager.engineState.value)
    }

    @Test
    fun `test bounded event logging`() {
        val manager = SampleTaskManager()
        for (i in 1..600) {
            manager.addEvent("test-task", EventType.SUCCESS, "Event #$i")
        }
        assertTrue(manager.events.value.size <= 500, "Events list must be capped at 500 entries")
    }
}
