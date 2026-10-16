package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.TickTask
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

class TickTaskE2ETarget(val executionLog: MutableList<Instant>) {
    @TickTask(intervalMs = 100, initialDelayMs = 100, id = "ksp-tick-e2e-task")
    fun onTick(ctx: TaskExecutionContext) {
        executionLog.add(ctx.trigger.scheduledAt)
    }
}

class KspTickTaskE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `TickTask KSP task executes periodically like FixedRate`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val target = TickTaskE2ETarget(executionLog)
        TickTaskE2ETarget_ChronoTaskBinding.register(engine, target)

        engine.start()

        timeSource.advanceBy(Duration.ofMillis(100))
        assertEquals(1, executionLog.size)

        timeSource.advanceBy(Duration.ofMillis(100))
        assertEquals(2, executionLog.size)

        timeSource.advanceBy(Duration.ofMillis(100))
        assertEquals(3, executionLog.size)

        engine.stop()
    }
}
