package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ReflectionConcurrencyE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class SkipIfRunningService {
        @Scheduled(intervalMs = 1000, concurrency = AnnotationConcurrency.SKIP_IF_RUNNING, id = "skip-service")
        suspend fun longTask() {
            delay(5000)
        }
    }

    class QueueOneService {
        @Scheduled(intervalMs = 1000, concurrency = AnnotationConcurrency.QUEUE_ONE, id = "queue-service")
        suspend fun longTask() {
            delay(5000)
        }
    }

    @Test
    fun `SkipIfRunning annotated task skips manual trigger while active`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val service = SkipIfRunningService()
        val handles = engine.registerAnnotated(service)
        val handle = handles.single()

        engine.start()

        // Manual trigger while execution is active
        val result = handle.triggerNow()
        assertInstanceOf(SubmissionResult.Skipped::class.java, result)

        engine.stop()
    }

    @Test
    fun `QueueOne annotated task queues next trigger while active`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val service = QueueOneService()
        val handles = engine.registerAnnotated(service)
        val handle = handles.single()

        engine.start()

        // Manual trigger while execution is active queues
        val result = handle.triggerNow()
        assertInstanceOf(SubmissionResult.Queued::class.java, result)

        engine.stop()
    }
}
