package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationMisfire
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

class ReflectionMisfireE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class FireOnceMisfireService(val triggerEvents: MutableList<Instant>) {
        @Scheduled(intervalMs = 60000, misfire = AnnotationMisfire.FIRE_ONCE, id = "misfire-annotated")
        fun onExecute(ctx: TaskExecutionContext) {
            triggerEvents.add(ctx.trigger.scheduledAt)
        }
    }

    @Test
    fun `FireOnce misfire policy produces 1 compensatory trigger after engine resume`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val triggerEvents = CopyOnWriteArrayList<Instant>()

        val service = FireOnceMisfireService(triggerEvents)
        engine.registerAnnotated(service)

        engine.start()
        assertEquals(1, triggerEvents.size) // T+0 trigger

        // Pause engine at 10:00:10
        timeSource.advanceBy(Duration.ofSeconds(10))
        engine.pause()

        // Advance 10 minutes while paused
        timeSource.advanceBy(Duration.ofMinutes(10)) // 10:10:10

        // Resume engine
        engine.resume()

        // FireOnce should produce 1 compensatory execution for 10:10:00
        assertEquals(2, triggerEvents.size)
        assertEquals(Instant.parse("2026-01-01T10:10:00Z"), triggerEvents[1])

        // Advance to 10:11:00
        timeSource.advanceBy(Duration.ofSeconds(50))
        assertEquals(3, triggerEvents.size)
        assertEquals(Instant.parse("2026-01-01T10:11:00Z"), triggerEvents[2])

        engine.stop()
    }
}
