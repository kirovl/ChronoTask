package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class LifecycleChurnStressTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testRapidEngineAndTaskLifecycleChurn() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Default)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val execCount = AtomicInteger(0)

        // Rapidly churn tasks: register, pause, resume, triggerNow, unregister
        val rounds = 50
        val deferreds = (1..rounds).map { i ->
            async(Dispatchers.Default) {
                val handle = engine.register(
                    TaskDefinition(
                        id = "churn-task-$i",
                        schedule = TaskSchedule.FixedRate(5000L),
                        executable = { execCount.incrementAndGet() }
                    )
                )

                handle.pause()
                handle.resume()
                handle.triggerNow()
                handle.cancel()
                engine.unregister("churn-task-$i")
            }
        }

        deferreds.awaitAll()

        // Also churn engine lifecycle
        engine.pause()
        assertEquals(EngineState.PAUSED, engine.state)
        engine.resume()
        assertEquals(EngineState.RUNNING, engine.state)

        engine.stop()
        assertEquals(EngineState.STOPPED, engine.state)
    }
}
