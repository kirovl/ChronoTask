package io.github.chronotask.dsl

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class TaskDslEngineE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `registers and executes Kotlin DSL task in engine with virtual time`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val triggerTimes = CopyOnWriteArrayList<Instant>()
        engine.schedule("dsl-task") {
            fixedRate(interval = 5.seconds, initialDelay = 5.seconds)
            execute { ctx ->
                triggerTimes.add(ctx.trigger.scheduledAt)
            }
        }

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
