package io.github.chronotask.engine

import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ChronoTaskEngineLifecycleTest {

    @Test
    fun `engine lifecycle transitions through CREATED RUNNING PAUSED STOPPED`() {
        val engine = ChronoTaskEngine.create()
        assertEquals(EngineState.CREATED, engine.state)

        engine.start()
        assertEquals(EngineState.RUNNING, engine.state)

        engine.pause()
        assertEquals(EngineState.PAUSED, engine.state)

        engine.resume()
        assertEquals(EngineState.RUNNING, engine.state)

        engine.stop()
        assertEquals(EngineState.STOPPED, engine.state)
    }

    @Test
    fun `STOPPED is terminal - subsequent transitions fail-fast`() {
        val engine = ChronoTaskEngine.create()
        engine.start()
        engine.stop()
        assertEquals(EngineState.STOPPED, engine.state)

        assertThrows(EngineStateException::class.java) { engine.start() }
        assertThrows(EngineStateException::class.java) { engine.resume() }
        assertThrows(EngineStateException::class.java) { engine.pause() }
        assertThrows(EngineStateException::class.java) {
            engine.register(
                TaskDefinition(
                    id = "task-after-stop",
                    name = "Task After Stop",
                    schedule = TaskSchedule.FixedRate(1000L),
                    executable = {}
                )
            )
        }
    }

    @Test
    fun `lifecycle methods are idempotent`() {
        val engine = ChronoTaskEngine.create()

        engine.start()
        engine.start() // no-op
        assertEquals(EngineState.RUNNING, engine.state)

        engine.pause()
        engine.pause() // no-op
        assertEquals(EngineState.PAUSED, engine.state)

        engine.resume()
        engine.resume() // no-op
        assertEquals(EngineState.RUNNING, engine.state)

        engine.stop()
        engine.stop() // no-op
        assertEquals(EngineState.STOPPED, engine.state)
    }
}
