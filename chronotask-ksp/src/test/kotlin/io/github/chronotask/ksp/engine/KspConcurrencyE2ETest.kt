package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationConcurrency
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

class ConcurrencyE2ETarget(val timeSource: VirtualTimeSource, val executionStarts: MutableList<Instant>) {
    @Scheduled(
        intervalMs = 1000,
        concurrency = AnnotationConcurrency.SKIP_IF_RUNNING,
        id = "ksp-skip-running-task"
    )
    suspend fun onExecute(ctx: TaskExecutionContext) {
        executionStarts.add(ctx.trigger.scheduledAt)
        // Task takes 2.5 seconds to execute
        timeSource.timer.delay(2500L)
    }
}

class KspConcurrencyE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `SkipIfRunning policy skips triggers that occur while task is running`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionStarts = CopyOnWriteArrayList<Instant>()

        val target = ConcurrencyE2ETarget(timeSource, executionStarts)
        ConcurrencyE2ETarget_ChronoTaskBinding.register(engine, target)

        engine.start()

        // 10:00:00: Execution 1 starts (runs until 10:00:02.500)
        assertEquals(1, executionStarts.size)

        // 10:00:01: Trigger 2 skipped because Execution 1 is running
        timeSource.advanceBy(Duration.ofSeconds(1))
        assertEquals(1, executionStarts.size)

        // 10:00:02: Trigger 3 skipped because Execution 1 is running
        timeSource.advanceBy(Duration.ofSeconds(1))
        assertEquals(1, executionStarts.size)

        // 10:00:02.500: Execution 1 finishes
        timeSource.advanceBy(Duration.ofMillis(500))
        assertEquals(1, executionStarts.size)

        // 10:00:03: Trigger 4 fires!
        timeSource.advanceBy(Duration.ofMillis(500))
        assertEquals(2, executionStarts.size)
        assertEquals(Instant.parse("2026-01-01T10:00:03Z"), executionStarts[1])

        engine.stop()
    }
}
