package io.github.chronotask.reflect.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.test.time.VirtualTimeSource
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class ReflectionTriggerNowE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    class TriggerService(val triggerSources: MutableList<TriggerSource>) {
        @Scheduled(intervalMs = 60000, initialDelayMs = 60000, id = "manual-trigger-service")
        fun onExecute(ctx: TaskExecutionContext) {
            triggerSources.add(ctx.trigger.source)
        }
    }

    @Test
    fun `triggerNow on annotated task handle dispatches MANUAL trigger immediately`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val triggerSources = CopyOnWriteArrayList<TriggerSource>()

        val service = TriggerService(triggerSources)
        val handles = engine.registerAnnotated(service)
        val handle = handles.single()

        engine.start()
        assertEquals(0, triggerSources.size)

        val result = handle.triggerNow()
        assertInstanceOf(SubmissionResult.Started::class.java, result)
        assertEquals(listOf(TriggerSource.MANUAL), triggerSources)

        engine.stop()
    }
}
