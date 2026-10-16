package io.github.chronotask.cron

import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.execution.SubmissionResult
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
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

class CronEngineE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")
    private val utc = ZoneId.of("UTC")

    @Test
    fun `Quartz Cron virtual-time progression`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val scheduledTriggers = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "quartz-cron-task",
            name = "Quartz Cron Task",
            schedule = TaskSchedule.Cron("*/10 * * * * ?", utc, CronDialect.QUARTZ),
            executable = { ctx ->
                scheduledTriggers.add(ctx.trigger.scheduledAt)
            }
        )

        engine.register(definition)
        engine.start()

        // Advance 9s -> nothing
        timeSource.advanceBy(Duration.ofSeconds(9))
        assertEquals(0, scheduledTriggers.size)

        // Advance 1s -> T + 10s
        timeSource.advanceBy(Duration.ofSeconds(1))
        assertEquals(listOf(Instant.parse("2026-01-01T10:00:10Z")), scheduledTriggers)

        // Advance 10s -> T + 20s
        timeSource.advanceBy(Duration.ofSeconds(10))
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:10Z"),
                Instant.parse("2026-01-01T10:00:20Z")
            ),
            scheduledTriggers
        )

        engine.stop()
    }

    @Test
    fun `UNIX Cron virtual-time progression`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val scheduledTriggers = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "unix-cron-task",
            name = "UNIX Cron Task",
            schedule = TaskSchedule.Cron("*/2 * * * *", utc, CronDialect.UNIX),
            executable = { ctx ->
                scheduledTriggers.add(ctx.trigger.scheduledAt)
            }
        )

        engine.register(definition)
        engine.start()

        // Advance 1 minute -> nothing (needs 2 mins)
        timeSource.advanceBy(Duration.ofMinutes(1))
        assertEquals(0, scheduledTriggers.size)

        // Advance 1 more minute -> T + 2m
        timeSource.advanceBy(Duration.ofMinutes(1))
        assertEquals(listOf(Instant.parse("2026-01-01T10:02:00Z")), scheduledTriggers)

        // Advance 2 more minutes -> T + 4m
        timeSource.advanceBy(Duration.ofMinutes(2))
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:02:00Z"),
                Instant.parse("2026-01-01T10:04:00Z")
            ),
            scheduledTriggers
        )

        engine.stop()
    }

    @Test
    fun `Cron task pause and resume lifecycle`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val executionCount = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "cron-pause-resume",
            name = "Cron Pause Resume",
            schedule = TaskSchedule.Cron("*/5 * * * * ?", utc, CronDialect.QUARTZ),
            executable = { ctx ->
                executionCount.add(ctx.trigger.scheduledAt)
            }
        )

        val handle = engine.register(definition)
        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(5)) // T + 5s
        assertEquals(1, executionCount.size)

        handle.pause()
        timeSource.advanceBy(Duration.ofSeconds(15)) // T + 20s
        assertEquals(1, executionCount.size) // No new executions while paused

        handle.resume()
        // Resuming at 10:00:20 (a valid schedule tick) triggers 10:00:20
        assertEquals(2, executionCount.size)
        assertEquals(Instant.parse("2026-01-01T10:00:20Z"), executionCount.last())

        timeSource.advanceBy(Duration.ofSeconds(5)) // T + 25s
        assertEquals(3, executionCount.size)
        assertEquals(Instant.parse("2026-01-01T10:00:25Z"), executionCount.last())

        engine.stop()
    }

    @Test
    fun `Cron task manual triggerNow executes immediately`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val triggers = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "cron-manual",
            name = "Cron Manual",
            schedule = TaskSchedule.Cron("0 0 12 * * ?", utc, CronDialect.QUARTZ), // Noon every day
            executable = { ctx ->
                triggers.add(ctx.trigger.scheduledAt)
            }
        )

        val handle = engine.register(definition)
        engine.start()

        assertEquals(0, triggers.size)

        // Trigger manually at 10:00:00
        val submission = handle.triggerNow()
        assertTrue(submission is SubmissionResult.Started)
        assertEquals(1, triggers.size)
        assertEquals(baseTime, triggers[0])

        // Normal schedule should still trigger at 12:00:00
        timeSource.advanceBy(Duration.ofHours(2))
        assertEquals(2, triggers.size)
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), triggers[1])

        engine.stop()
    }

    @Test
    fun `Cron task misfire handling with FireOnce`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTaskEngine.create(config)

        val triggers = CopyOnWriteArrayList<Instant>()
        val definition = TaskDefinition(
            id = "cron-misfire",
            name = "Cron Misfire",
            schedule = TaskSchedule.Cron("*/5 * * * * ?", utc, CronDialect.QUARTZ),
            misfirePolicy = MisfirePolicy.FireOnce,
            executable = { ctx ->
                triggers.add(ctx.trigger.scheduledAt)
            }
        )

        engine.register(definition)
        engine.start()

        // Fast-forward time by 60 seconds (missing multiple 5s triggers)
        timeSource.advanceBy(Duration.ofSeconds(60))

        // With FireOnce, misfire generates execution for the missed trigger
        assertTrue(triggers.isNotEmpty())

        engine.stop()
    }
}
