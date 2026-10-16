package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ReflectionOnceE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class OnceService {
        val executionCount = AtomicInteger(0)

        @Once(delayMs = 5000, id = "once-e2e")
        fun onExecute() {
            executionCount.incrementAndGet()
        }
    }

    @Test
    fun `Once annotated task executes exactly once and scheduler finishes`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val service = OnceService()
        val handles = engine.registerAnnotated(service)
        val handle = handles.single()

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(4))
        assertEquals(0, service.executionCount.get())

        timeSource.advanceBy(Duration.ofSeconds(1)) // T+5s
        assertEquals(1, service.executionCount.get())

        // Advance 100 days
        timeSource.advanceBy(Duration.ofDays(100))
        assertEquals(1, service.executionCount.get(), "Once task must never execute again")

        val snapshot = handle.snapshot()
        assertTrue(snapshot.scheduleCompleted)

        engine.stop()
    }
}
