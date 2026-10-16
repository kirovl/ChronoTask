package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class OnceE2ETarget(val executionLog: MutableList<Instant>) {
    @Once(delayMs = 2000, id = "ksp-once-e2e-task")
    fun onOnce(ctx: TaskExecutionContext) {
        executionLog.add(ctx.trigger.scheduledAt)
    }
}

class KspOnceE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `Once KSP task triggers exactly once and marks schedule completed`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val target = OnceE2ETarget(executionLog)
        val handles = OnceE2ETarget_ChronoTaskBinding.register(engine, target)
        val handle = handles.single()

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(1))
        assertEquals(0, executionLog.size)

        timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:02
        assertEquals(listOf(Instant.parse("2026-01-01T10:00:02Z")), executionLog)

        // Advance 100 days -> no further executions
        timeSource.advanceBy(Duration.ofDays(100))
        assertEquals(1, executionLog.size)

        val snapshot = handle.snapshot()
        assertTrue(snapshot.scheduleCompleted)

        engine.stop()
    }
}
