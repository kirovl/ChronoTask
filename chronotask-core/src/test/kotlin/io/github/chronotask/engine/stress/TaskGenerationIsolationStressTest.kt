package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.model.TaskState
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class TaskGenerationIsolationStressTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testStaleTaskHandleCannotAffectNewGeneration() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val gen1Exec = AtomicInteger(0)
        val gen2Exec = AtomicInteger(0)

        // Register generation 1
        val handleGen1 = engine.register(
            TaskDefinition(
                id = "gen-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
                executable = { gen1Exec.incrementAndGet() }
            )
        )

        // Advance 2 steps
        timeSource.advanceBy(2000L)
        assertEquals(2, gen1Exec.get())

        // Unregister gen 1
        val unregistered = engine.unregister("gen-task")
        assertTrue(unregistered)

        // Register generation 2 with same ID
        val handleGen2 = engine.register(
            TaskDefinition(
                id = "gen-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
                executable = { gen2Exec.incrementAndGet() }
            )
        )

        // Stale handleGen1 tries to cancel
        handleGen1.cancel()
        assertEquals(TaskState.CANCELLED, handleGen1.state)

        // Advance time for gen 2
        timeSource.advanceBy(2000L)
        assertEquals(2, gen2Exec.get(), "Gen 2 must continue executing unaffected by stale gen 1 handle cancel")

        engine.stop()
    }
}
