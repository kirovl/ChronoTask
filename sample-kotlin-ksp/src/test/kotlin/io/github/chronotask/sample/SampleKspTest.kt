package io.github.chronotask.sample

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.ChronoTaskEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SampleKspTest {

    private lateinit var engine: ChronoTaskEngine

    @BeforeEach
    fun setUp() {
        engine = ChronoTask.create()
        engine.start()
    }

    @AfterEach
    fun tearDown() {
        engine.stop()
    }

    @Test
    fun `sample demonstrates definitions and registration with generated KSP binding`() = runBlocking {
        val services = SampleKspServices()

        // 1. Test definitions() pure function
        val definitions = SampleKspServices_ChronoTaskBinding.definitions(services)
        assertEquals(4, definitions.size)

        // 2. Test register() function
        val handles = SampleKspServices_ChronoTaskBinding.register(engine, services)
        assertEquals(4, handles.size)

        assertNotNull(engine.getHandle("sample-cleanup"))
        assertNotNull(engine.getHandle("sample-cron-report"))
        assertNotNull(engine.getHandle("sample-init-task"))
        assertNotNull(engine.getHandle("sample-game-tick"))

        // Trigger tasks manually to verify execution
        engine.getHandle("sample-cleanup")?.triggerNow()
        engine.getHandle("sample-init-task")?.triggerNow()
        engine.getHandle("sample-game-tick")?.triggerNow()

        delay(100)

        assertTrue(services.cleanupCount.get() >= 1)
        assertTrue(services.initCount.get() >= 1)
        assertTrue(services.tickCount.get() >= 1)
    }
}
