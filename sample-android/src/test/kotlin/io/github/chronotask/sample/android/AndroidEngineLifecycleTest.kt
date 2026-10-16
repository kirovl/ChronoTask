package io.github.chronotask.sample.android

import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskState
import io.github.chronotask.sample.android.model.SampleTaskManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidEngineLifecycleTest {

    @Test
    fun `test engine lifecycle state machine in android sample`() {
        val manager = SampleTaskManager()
        assertEquals(EngineState.CREATED, manager.engineState.value)

        manager.start()
        assertEquals(EngineState.RUNNING, manager.engineState.value)

        manager.pause()
        assertEquals(EngineState.PAUSED, manager.engineState.value)

        manager.resume()
        assertEquals(EngineState.RUNNING, manager.engineState.value)

        manager.stop()
        assertEquals(EngineState.STOPPED, manager.engineState.value)
    }
}
