package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Cron
import io.github.chronotask.cron.CronDialect
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

class ReflectionCronE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class CronService(val executionLog: MutableList<Instant>) {
        @Cron(expression = "0 0 * * * ?", zone = "UTC", dialect = CronDialect.QUARTZ, id = "cron-hourly")
        fun onHour(ctx: TaskExecutionContext) {
            executionLog.add(ctx.trigger.scheduledAt)
        }
    }

    @Test
    fun `Cron annotated task triggers on cron schedule in engine`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val service = CronService(executionLog)
        engine.registerAnnotated(service)

        engine.start()

        // 10:00:00 base -> next trigger is 11:00:00 (+1 hour)
        timeSource.advanceBy(Duration.ofMinutes(59))
        assertEquals(0, executionLog.size)

        timeSource.advanceBy(Duration.ofMinutes(1)) // 11:00:00
        assertEquals(listOf(Instant.parse("2026-01-01T11:00:00Z")), executionLog)

        timeSource.advanceBy(Duration.ofHours(1)) // 12:00:00
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
