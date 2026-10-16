package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class StopRaceAndGhostExecutionTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testNoGhostExecutionsAfterEngineStopWhenTimeAdvances() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val execCount = AtomicInteger(0)
        engine.register(
            TaskDefinition(
                id = "ghost-guard-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
                executable = { execCount.incrementAndGet() }
            )
        )

        engine.start()

        // Advance 5 steps -> 5 executions
        for (i in 1..5) {
            timeSource.advanceBy(1000L)
        }
        assertEquals(5, execCount.get())

        // Stop engine
        engine.stop()
        assertEquals(EngineState.STOPPED, engine.state)

        // Advance virtual time by 1 year (31,536,000s)
        timeSource.advanceBy(Duration.ofDays(365))

        // Executions MUST NOT have increased
        assertEquals(5, execCount.get(), "No ghost executions must occur after engine is stopped")
    }

    @Test
    fun testStopRacedWithConcurrentOperations() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Default)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val handle = engine.register(
            TaskDefinition(
                id = "race-task",
                schedule = TaskSchedule.FixedRate(1000L),
                executable = { }
            )
        )

        val stopJob = async(Dispatchers.Default) { engine.stop() }
        val triggerJob = async(Dispatchers.Default) {
            try { handle.triggerNow() } catch (_: Throwable) {}
        }
        val registerJob = async(Dispatchers.Default) {
            try {
                engine.register(
                    TaskDefinition(
                        id = "late-task",
                        schedule = TaskSchedule.FixedRate(1000L),
                        executable = { }
                    )
                )
            } catch (_: Throwable) {}
        }

        listOf(stopJob, triggerJob, registerJob).awaitAll()
        assertEquals(EngineState.STOPPED, engine.state)
    }
}
