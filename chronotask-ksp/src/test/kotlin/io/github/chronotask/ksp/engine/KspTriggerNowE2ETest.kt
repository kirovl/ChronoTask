package io.github.chronotask.ksp.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.test.time.VirtualTimeSource
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class TriggerNowE2ETarget(val triggerSources: MutableList<TriggerSource>) {
    @Scheduled(intervalMs = 60000, initialDelayMs = 60000, id = "ksp-manual-trigger-service")
    fun onExecute(ctx: TaskExecutionContext) {
        triggerSources.add(ctx.trigger.source)
    }
}

class KspTriggerNowE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `triggerNow on KSP task handle dispatches MANUAL trigger immediately`() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        val triggerSources = CopyOnWriteArrayList<TriggerSource>()

        val target = TriggerNowE2ETarget(triggerSources)
        val handles = TriggerNowE2ETarget_ChronoTaskBinding.register(engine, target)
        val handle = handles.single()

        engine.start()
        assertEquals(0, triggerSources.size)

        val result = handle.triggerNow()
        assertInstanceOf(SubmissionResult.Started::class.java, result)
        assertEquals(listOf(TriggerSource.MANUAL), triggerSources)

        engine.stop()
    }
}
