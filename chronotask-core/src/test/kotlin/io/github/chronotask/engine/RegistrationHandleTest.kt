package io.github.chronotask.engine

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.model.TaskState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RegistrationHandleTest {
    @Test fun staleHandleCannotUnregisterReplacementWithSameId() {
        val engine = DefaultChronoTaskEngine()
        val definition = TaskDefinition("same", schedule = TaskSchedule.Once(0), isEnabled = false, executable = {})
        try {
            val old = engine.register(definition)
            val alias = engine.getHandle("same")!!
            assertTrue(old.unregister())
            assertNull(engine.getHandle("same"))
            assertEquals(TaskState.CANCELLED, old.state)
            val replacement = engine.register(definition)
            assertFalse(old.unregister())
            assertFalse(alias.unregister())
            assertNotNull(engine.getHandle("same"))
            assertNotEquals(TaskState.CANCELLED, replacement.state)
            assertTrue(replacement.unregister())
            assertFalse(replacement.unregister())
        } finally { engine.stop() }
    }
}
