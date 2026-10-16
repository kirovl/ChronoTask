package io.github.chronotask.engine

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.model.TaskState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskUnregisterTest {

    @Test
    fun `unregister removes task and cancels its handle`() {
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition("task-unreg", "Unreg Task", TaskSchedule.FixedRate(1000L), executable = {})
        val handle = engine.register(def)

        assertTrue(engine.unregister("task-unreg"))
        assertNull(engine.getHandle("task-unreg"))
        assertFalse(engine.unregister("task-unreg")) // already unregistered

        assertEquals(TaskState.CANCELLED, handle.state)
        engine.stop()
    }

    @Test
    fun `re-registering same ID creates isolated new runtime and isolates stale handle`() {
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def1 = TaskDefinition("same-id", "First Version", TaskSchedule.FixedRate(1000L), executable = {})
        val handle1 = engine.register(def1)
        engine.unregister("same-id")

        val def2 = TaskDefinition("same-id", "Second Version", TaskSchedule.FixedRate(5000L), executable = {})
        val handle2 = engine.register(def2)

        // Handle1 is cancelled and cannot control Handle2's runtime
        assertEquals(TaskState.CANCELLED, handle1.state)
        assertEquals(TaskState.SCHEDULED, handle2.state)

        handle1.pause()
        assertEquals(TaskState.SCHEDULED, handle2.state, "handle1.pause() must not affect handle2")

        engine.stop()
    }
}
