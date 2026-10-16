package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ExceptionAndCancellationStormTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testTaskExceptionStormDoesNotCrashEngine() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val caughtErrors = AtomicInteger(0)

        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined),
            errorHandler = { _, _, _ -> caughtErrors.incrementAndGet() }
        )
        val engine = ChronoTask.create(config)

        val healthyTaskTicks = AtomicInteger(0)

        // Register 10 failing tasks
        for (i in 1..10) {
            engine.register(
                TaskDefinition(
                    id = "failing-task-$i",
                    schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
                    executable = { throw RuntimeException("Simulated failure in task $i") }
                )
            )
        }

        // Register 1 healthy task
        engine.register(
            TaskDefinition(
                id = "healthy-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
                executable = { healthyTaskTicks.incrementAndGet() }
            )
        )

        engine.start()

        // Advance 10 steps -> 10 failing tasks * 10 steps = 100 errors; 1 healthy task * 10 steps = 10 ticks
        for (step in 1..10) {
            timeSource.advanceBy(1000L)
        }

        assertEquals(100, caughtErrors.get())
        assertEquals(10, healthyTaskTicks.get(), "Healthy task must continue executing uninterrupted by failing peers")

        engine.stop()
    }

    @Test
    fun testListenerFailureStormIsIsolated() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val buggyListener = object : ExecutionListener {
            override fun onStarted(definition: TaskDefinition, execution: TaskExecution) {
                throw RuntimeException("Buggy listener onStarted")
            }

            override fun onSuccess(definition: TaskDefinition, execution: TaskExecution) {
                throw RuntimeException("Buggy listener onSuccess")
            }
        }

        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined),
            executionListener = buggyListener
        )
        val engine = ChronoTask.create(config)

        val taskExecutions = AtomicInteger(0)
        engine.register(
            TaskDefinition(
                id = "listener-storm-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
                executable = { taskExecutions.incrementAndGet() }
            )
        )

        engine.start()

        // Advance 5 steps
        for (step in 1..5) {
            timeSource.advanceBy(1000L)
        }

        assertEquals(5, taskExecutions.get(), "Task executions must succeed despite listener throwing exceptions")

        engine.stop()
    }
}
