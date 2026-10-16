package io.github.chronotask.job

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

class ManagedCronJobEngineE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    private class HourlyCronJob(val executionLog: MutableList<Instant>) : ManagedCronJob(
        id = "hourly-job",
        cron = "0 0 * * * ?",
        zoneId = ZoneId.of("UTC")
    ) {
        override suspend fun execute(context: TaskExecutionContext) {
            executionLog.add(context.trigger.scheduledAt)
        }
    }

    @Test
    fun `registers ManagedCronJob and triggers on cron schedule`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val job = HourlyCronJob(executionLog)
        engine.register(job)

        engine.start()

        // Hourly job base is 10:00:00 -> Next cron trigger is 11:00:00 (+1 hour)
        timeSource.advanceBy(Duration.ofMinutes(59))
        assertEquals(0, executionLog.size)

        timeSource.advanceBy(Duration.ofMinutes(1)) // T + 1 hour -> 11:00:00
        assertEquals(listOf(Instant.parse("2026-01-01T11:00:00Z")), executionLog)

        timeSource.advanceBy(Duration.ofHours(1)) // T + 2 hours -> 12:00:00
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T11:00:00Z"),
                Instant.parse("2026-01-01T12:00:00Z")
            ),
            executionLog
        )

        engine.stop()
    }
}
