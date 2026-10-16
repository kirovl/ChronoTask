package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationMisfire
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

class MisfireE2ETarget(val executionLog: MutableList<Instant>) {
    @Scheduled(intervalMs = 60000, misfire = AnnotationMisfire.FIRE_ONCE, id = "ksp-misfire-task")
    fun onExecute(ctx: TaskExecutionContext) {
        executionLog.add(ctx.trigger.scheduledAt)
    }
}

class KspMisfireE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `Misfire FIRE_ONCE dispatches compensatory execution upon engine resume`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val target = MisfireE2ETarget(executionLog)
        MisfireE2ETarget_ChronoTaskBinding.register(engine, target)

        engine.start()

        // 10:00:00 initial trigger
        assertEquals(1, executionLog.size)

        // Pause engine at 10:00:10
        timeSource.advanceBy(Duration.ofSeconds(10))
        engine.pause()

        // Advance 10 minutes while paused -> missed 10:01, 10:02, ..., 10:10
        timeSource.advanceBy(Duration.ofMinutes(10))

        // Resume engine -> FireOnce produces 1 compensatory trigger for last missed (10:10:00)
        engine.resume()
        assertEquals(2, executionLog.size)
        assertEquals(Instant.parse("2026-01-01T10:10:00Z"), executionLog[1])

        // Advance to 10:11:00 -> next normal trigger
        timeSource.advanceBy(Duration.ofSeconds(50))
        assertEquals(3, executionLog.size)
        assertEquals(Instant.parse("2026-01-01T10:11:00Z"), executionLog[2])

        engine.stop()
    }
}
