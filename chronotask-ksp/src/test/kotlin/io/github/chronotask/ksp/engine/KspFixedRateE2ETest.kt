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

class FixedRateE2ETarget(val timestamps: MutableList<Instant>) {
    @Scheduled(
        intervalMs = 5000,
        initialDelayMs = 5000,
        mode = ScheduleMode.FIXED_RATE,
        id = "ksp-rate-e2e-task"
    )
    fun onRate(ctx: TaskExecutionContext) {
        timestamps.add(ctx.trigger.scheduledAt)
    }
}

class KspFixedRateE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `FixedRate KSP task schedules accurately across virtual time progression`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val timestamps = CopyOnWriteArrayList<Instant>()

        val target = FixedRateE2ETarget(timestamps)
        FixedRateE2ETarget_ChronoTaskBinding.register(engine, target)

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(4))
        assertEquals(0, timestamps.size)

        timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:05
        assertEquals(listOf(Instant.parse("2026-01-01T10:00:05Z")), timestamps)

        timeSource.advanceBy(Duration.ofSeconds(5)) // 10:00:10
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:05Z"),
                Instant.parse("2026-01-01T10:00:10Z")
            ),
            timestamps
        )

        timeSource.advanceBy(Duration.ofSeconds(5)) // 10:00:15
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:05Z"),
                Instant.parse("2026-01-01T10:00:10Z"),
                Instant.parse("2026-01-01T10:00:15Z")
            ),
            timestamps
        )

        engine.stop()
    }
}
