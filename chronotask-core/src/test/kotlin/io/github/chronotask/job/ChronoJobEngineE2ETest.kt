package io.github.chronotask.job

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class ChronoJobEngineE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    private class TestHeartbeatJob(val executionLog: MutableList<Instant>) : ChronoJob() {
        override val id: String = "heartbeat-job"
        override val schedule: TaskSchedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 5000L)

        override suspend fun execute(context: TaskExecutionContext) {
            executionLog.add(context.trigger.scheduledAt)
        }
    }

    @Test
    fun `registers ChronoJob and executes on schedule in engine`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val job = TestHeartbeatJob(executionLog)
        val handle = engine.register(job)

        assertEquals("heartbeat-job", handle.taskId)

        engine.start()

        timeSource.advanceBy(Duration.ofSeconds(5)) // T + 5s
        assertEquals(listOf(Instant.parse("2026-01-01T10:00:05Z")), executionLog)

        timeSource.advanceBy(Duration.ofSeconds(10)) // T + 15s
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T10:00:05Z"),
                Instant.parse("2026-01-01T10:00:10Z"),
                Instant.parse("2026-01-01T10:00:15Z")
            ),
            executionLog
        )

        engine.stop()
    }
}
