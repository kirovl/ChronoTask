package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class ReflectionFixedRateE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class RateService(val triggerTimes: MutableList<Instant>) {
        @Scheduled(intervalMs = 5000, initialDelayMs = 5000, mode = ScheduleMode.FIXED_RATE, id = "rate-service-task")
        fun onTick(ctx: TaskExecutionContext) {
            triggerTimes.add(ctx.trigger.scheduledAt)
        }
    }

    @Test
    fun `FixedRate annotated task executes on schedule in engine`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val triggerTimes = CopyOnWriteArrayList<Instant>()

        val service = RateService(triggerTimes)
        val handles = engine.registerAnnotated(service)
        assertEquals(1, handles.size)

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(5)) // T + 5s
        assertEquals(listOf(Instant.parse("2026-01-01T10:00:05Z")), triggerTimes)

        timeSource.advanceBy(Duration.ofSeconds(10)) // T + 15s
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:05Z"),
                Instant.parse("2026-01-01T10:00:10Z"),
                Instant.parse("2026-01-01T10:00:15Z")
            ),
            triggerTimes
        )

        engine.stop()
    }
}
