package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class FixedDelayE2ETarget(val timeSource: VirtualTimeSource, val timestamps: MutableList<Instant>) {
    @Scheduled(
        intervalMs = 10000,
        initialDelayMs = 5000,
        mode = ScheduleMode.FIXED_DELAY,
        id = "ksp-delay-e2e-task"
    )
    suspend fun onDelay(ctx: TaskExecutionContext) {
        timestamps.add(ctx.trigger.scheduledAt)
        timeSource.timer.delay(3000L)
    }
}

class KspFixedDelayE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `FixedDelay KSP task maintains completion dependent delay`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val timestamps = CopyOnWriteArrayList<Instant>()

        val target = FixedDelayE2ETarget(timeSource, timestamps)
        FixedDelayE2ETarget_ChronoTaskBinding.register(engine, target)

        engine.start()

        // Advance to initialDelay (T+5s) -> Execution 1 starts at T+5s and finishes at T+8s
        timeSource.advanceBy(Duration.ofSeconds(5))
        assertEquals(1, timestamps.size)
        assertEquals(Instant.parse("2026-01-01T10:00:05Z"), timestamps[0])

        // Advance 3s -> execution 1 finishes at 10:00:08, next trigger scheduled for 10:00:18!
        timeSource.advanceBy(Duration.ofSeconds(3))

        // At T+15s (10:00:15), Execution 2 should NOT have started yet
        timeSource.advanceBy(Duration.ofSeconds(7))
        assertEquals(1, timestamps.size)

        // At T+18s (10:00:18), Execution 2 triggers!
        timeSource.advanceBy(Duration.ofSeconds(3))
        assertEquals(2, timestamps.size)
        assertEquals(Instant.parse("2026-01-01T10:00:18Z"), timestamps[1])

        engine.stop()
    }
}
