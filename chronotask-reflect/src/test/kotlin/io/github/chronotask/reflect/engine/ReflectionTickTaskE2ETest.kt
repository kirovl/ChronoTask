package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.TickTask
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

class ReflectionTickTaskE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class HeartbeatService(val ticks: MutableList<Instant>) {
        @TickTask(intervalMs = 1000, initialDelayMs = 1000, id = "heartbeat-tick")
        suspend fun tick(ctx: TaskExecutionContext) {
            ticks.add(ctx.trigger.scheduledAt)
        }
    }

    @Test
    fun `TickTask annotated method executes as FixedRate with zero extra scheduling semantics`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val ticks = CopyOnWriteArrayList<Instant>()

        val service = HeartbeatService(ticks)
        engine.registerAnnotated(service)

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(3))
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:01Z"),
                Instant.parse("2026-01-01T10:00:02Z"),
                Instant.parse("2026-01-01T10:00:03Z")
            ),
            ticks
        )

        engine.stop()
    }
}
