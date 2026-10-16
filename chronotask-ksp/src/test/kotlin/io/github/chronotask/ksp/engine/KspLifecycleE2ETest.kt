package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
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

class LifecycleE2ETarget(val executionLog: MutableList<Instant>) {
    @Scheduled(intervalMs = 1000, initialDelayMs = 1000, id = "ksp-lifecycle-service")
    fun onExecute(ctx: TaskExecutionContext) {
        executionLog.add(ctx.trigger.scheduledAt)
    }
}

class KspLifecycleE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `KSP task pause, resume, and cancel lifecycle`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val target = LifecycleE2ETarget(executionLog)
        val handle = LifecycleE2ETarget_ChronoTaskBinding.register(engine, target).single()

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:01
        assertEquals(1, executionLog.size)
        timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:02
        assertEquals(2, executionLog.size)

        // Pause task
        handle.pause()
        timeSource.advanceBy(Duration.ofSeconds(5))
        assertEquals(2, executionLog.size, "No triggers while paused")

        // Resume task -> triggers resume execution
        handle.resume()
        assertEquals(3, executionLog.size)

        // Next tick
        timeSource.advanceBy(Duration.ofSeconds(1))
        assertEquals(4, executionLog.size)

        // Cancel task
        handle.cancel()
        timeSource.advanceBy(Duration.ofSeconds(10))
        assertEquals(4, executionLog.size, "No triggers after cancel")

        engine.stop()
    }
}
