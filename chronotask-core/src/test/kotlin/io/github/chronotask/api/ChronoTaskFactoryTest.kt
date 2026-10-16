package io.github.chronotask.api

import io.github.chronotask.ChronoTask
import io.github.chronotask.builder.TaskBuilder
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.EngineState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ChronoTaskFactoryTest {

    @Test
    fun `ChronoTask create returns configured ChronoTaskEngine`() {
        val engine = ChronoTask.create()
        assertNotNull(engine)
        assertInstanceOf(ChronoTaskEngine::class.java, engine)
        assertEquals(EngineState.CREATED, engine.state)
        engine.stop()
    }

    @Test
    fun `ChronoTask create with EngineConfig succeeds`() {
        val config = EngineConfig()
        val engine = ChronoTask.create(config)
        assertNotNull(engine)
        assertEquals(EngineState.CREATED, engine.state)
        engine.stop()
    }

    @Test
    fun `ChronoTask newTask creates TaskBuilder with id`() {
        val builder = ChronoTask.newTask("sample-task")
        assertNotNull(builder)
        assertInstanceOf(TaskBuilder::class.java, builder)
    }
}
