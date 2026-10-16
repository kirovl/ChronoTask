package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Cron
import io.github.chronotask.cron.CronDialect
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

class CronE2ETarget(val executionLog: MutableList<Instant>) {
    @Cron(expression = "0 0 * * * ?", zone = "UTC", dialect = CronDialect.QUARTZ, id = "ksp-cron-hourly")
    fun onHour(ctx: TaskExecutionContext) {
        executionLog.add(ctx.trigger.scheduledAt)
    }
}

class KspCronE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `Cron KSP task triggers on cron schedule in engine`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val executionLog = CopyOnWriteArrayList<Instant>()

        val target = CronE2ETarget(executionLog)
        CronE2ETarget_ChronoTaskBinding.register(engine, target)

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
