package io.github.chronotask.sample.android

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.DefaultDispatcherProvider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.github.chronotask.model.EngineState
import io.github.chronotask.sample.android.tasks.KspSampleTasks
import io.github.chronotask.sample.android.tasks.KspSampleTasks_ChronoTaskBinding
import io.github.chronotask.test.time.VirtualTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidKspExecutionTest {

    @Test
    fun `test ksp suspend task and context injection in android`() = runTest {
        val virtualTime = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val engine = ChronoTask.create(
            EngineConfig(clock = virtualTime.clock, timer = virtualTime.timer,
                dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        )

        val executed = CopyOnWriteArrayList<String>()
        val tasks = KspSampleTasks { id, _, _ ->
            executed.add(id)
        }

        val handles = KspSampleTasks_ChronoTaskBinding.register(engine, tasks)
        assertEquals(5, handles.size, "Should register 5 KSP tasks")

        engine.start()
        assertEquals(EngineState.RUNNING, engine.state)

        // Advance 1.5s (triggers ksp-onboarding-tip and ksp-location-beacon)
        virtualTime.advanceBy(1500L)
        testScheduler.advanceUntilIdle()
        assertTrue(executed.contains("ksp-onboarding-tip"))

        // Advance to 3s (triggers ksp-analytics-sync suspend task)
        virtualTime.advanceBy(1500L)
        testScheduler.advanceUntilIdle()
        assertTrue(executed.contains("ksp-analytics-sync"))

        engine.stop()
    }
}
