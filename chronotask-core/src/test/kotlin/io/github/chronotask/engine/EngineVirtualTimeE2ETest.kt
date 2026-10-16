package io.github.chronotask.engine

import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class EngineVirtualTimeE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `FixedRate virtual-time E2E progression and no drift`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val executionTimestamps = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "fixed-rate-e2e",
            name = "FixedRate E2E",
            schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 5000L),
            executable = { ctx ->
                executionTimestamps.add(ctx.trigger.scheduledAt)
            }
        )

        engine.register(definition)
        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(4))
        assertEquals(0, executionTimestamps.size)

        timeSource.advanceBy(Duration.ofSeconds(1)) // T + 5s
        assertEquals(listOf(Instant.parse("2026-01-01T10:00:05Z")), executionTimestamps)

        timeSource.advanceBy(Duration.ofSeconds(5)) // T + 10s
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:05Z"),
                Instant.parse("2026-01-01T10:00:10Z")
            ),
            executionTimestamps
        )

        timeSource.advanceBy(Duration.ofSeconds(5)) // T + 15s
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:05Z"),
                Instant.parse("2026-01-01T10:00:10Z"),
                Instant.parse("2026-01-01T10:00:15Z")
            ),
            executionTimestamps
        )

        engine.stop()
    }

    @Test
    fun `FixedDelay completion feedback scheduling`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val scheduledTimestamps = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "fixed-delay-e2e",
            name = "FixedDelay E2E",
            schedule = TaskSchedule.FixedDelay(delayMs = 10000L, initialDelayMs = 5000L),
            executable = { ctx ->
                scheduledTimestamps.add(ctx.trigger.scheduledAt)
                // Simulate 3 seconds execution time via timer
                timeSource.timer.delay(3000L)
            }
        )

        engine.register(definition)
        engine.start()

        // Advance to initialDelay (T+5s) -> Execution 1 starts at T+5s and completes at T+8s
        timeSource.advanceBy(Duration.ofSeconds(5))
        assertEquals(1, scheduledTimestamps.size)
        assertEquals(Instant.parse("2026-01-01T10:00:05Z"), scheduledTimestamps[0])

        // Advance 3s -> execution 1 finishes at 10:00:08, next trigger scheduled for 10:00:18!
        timeSource.advanceBy(Duration.ofSeconds(3))

        // At T+15s (10:00:15), Execution 2 should NOT have started yet!
        timeSource.advanceBy(Duration.ofSeconds(7)) // now at 10:00:15
        assertEquals(1, scheduledTimestamps.size)

        // At T+18s (10:00:18), Execution 2 triggers!
        timeSource.advanceBy(Duration.ofSeconds(3)) // now at 10:00:18
        assertEquals(2, scheduledTimestamps.size)
        assertEquals(Instant.parse("2026-01-01T10:00:18Z"), scheduledTimestamps[1])

        engine.stop()
    }

    @Test
    fun `Pause and Resume with Misfire FireOnce`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val triggerEvents = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "misfire-task",
            name = "Misfire Task",
            schedule = TaskSchedule.FixedRate(intervalMs = 60000L), // 1 minute
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            executable = { ctx ->
                triggerEvents.add(ctx.trigger.scheduledAt)
            }
        )

        engine.register(definition)
        engine.start()

        // 10:00:00 triggers
        assertEquals(1, triggerEvents.size)

        // Pause engine at 10:00:10
        timeSource.advanceBy(Duration.ofSeconds(10))
        engine.pause()

        // Advance 10 minutes while paused -> missed 10:01, 10:02, ..., 10:10
        timeSource.advanceBy(Duration.ofMinutes(10)) // now 10:10:10

        // Resume engine
        engine.resume()

        // FireOnce should produce 1 compensatory execution for last missed (10:10:00)
        assertEquals(2, triggerEvents.size)
        assertEquals(Instant.parse("2026-01-01T10:10:00Z"), triggerEvents[1])

        // Advance to 10:11:00 -> next normal trigger
        timeSource.advanceBy(Duration.ofSeconds(50))
        assertEquals(3, triggerEvents.size)
        assertEquals(Instant.parse("2026-01-01T10:11:00Z"), triggerEvents[2])

        engine.stop()
    }

    @Test
    fun `365-day Post Stop advance produces zero executions`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val executionCount = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "stop-e2e-task",
            name = "Stop Task",
            schedule = TaskSchedule.FixedRate(intervalMs = 1000L),
            executable = { ctx -> executionCount.add(ctx.trigger.scheduledAt) }
        )

        engine.register(definition)
        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(5))
        val countAtStop = executionCount.size
        assertTrue(countAtStop > 0)

        engine.stop()

        // Advance 365 days into the future
        timeSource.advanceBy(Duration.ofDays(365))

        assertEquals(countAtStop, executionCount.size, "No new executions must occur after stop")
        assertEquals(0, timeSource.waiterCount())
    }
}
