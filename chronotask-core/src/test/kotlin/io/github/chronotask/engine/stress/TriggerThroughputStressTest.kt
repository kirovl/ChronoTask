package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class TriggerThroughputStressTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testHighThroughputTriggerEventsAcrossManyTasks() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val taskCount = 100
        val executionCounts = Array(taskCount) { AtomicInteger(0) }

        for (i in 0 until taskCount) {
            val idx = i
            engine.register(
                TaskDefinition(
                    id = "tp-task-$idx",
                    schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L), // Every 1s
                    executable = {
                        executionCounts[idx].incrementAndGet()
                    }
                )
            )
        }

        engine.start()

        // Advance time in 1-second steps for 100 steps -> 100 tasks * 100 intervals = 10,000 trigger events
        val steps = 100
        for (step in 1..steps) {
            timeSource.advanceBy(1000L)
        }

        // Verify each task executed exactly 100 times
        var totalExecutions = 0
        for (i in 0 until taskCount) {
            val count = executionCounts[i].get()
            assertEquals(steps, count, "Task $i should have executed $steps times")
            totalExecutions += count
        }
        assertEquals(taskCount * steps, totalExecutions)

        engine.stop()
    }
}
