package io.github.chronotask.sample.android

import io.github.chronotask.ChronoTask
import io.github.chronotask.dsl.schedule
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.DefaultDispatcherProvider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskState
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.sample.android.tasks.KspSampleTasks
import io.github.chronotask.sample.android.tasks.KspSampleTasks_ChronoTaskBinding
import io.github.chronotask.sample.android.tasks.ReflectionSampleTasks
import io.github.chronotask.test.time.VirtualTimeSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidChronoTaskTest {

    @Test
    fun `test ksp binding in android sample`() = runTest {
        val virtualTime = VirtualTimeSource(Instant.parse("2026-09-21T12:00:00Z"))
        val engine = ChronoTask.create(
            EngineConfig(clock = virtualTime.clock, timer = virtualTime.timer,
                dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        )

        val executedTasks = CopyOnWriteArrayList<String>()
        val tasks = KspSampleTasks { taskId, _, _ ->
            executedTasks.add(taskId)
        }

        val handles = KspSampleTasks_ChronoTaskBinding.register(engine, tasks)
        assertEquals(5, handles.size)

        engine.start()
        assertEquals(EngineState.RUNNING, engine.state)

        // Advance virtual time by 1.5 seconds to trigger once task and first scheduled
        virtualTime.advanceBy(1500L)
        testScheduler.advanceUntilIdle()

        assertTrue(executedTasks.contains("ksp-onboarding-tip"))
        assertFalse(executedTasks.contains("ksp-location-beacon"), "Beacon initial delay is 2 seconds")

        // Advance to 3s to trigger analytics sync
        virtualTime.advanceBy(1500L)
        testScheduler.advanceUntilIdle()
        assertTrue(executedTasks.contains("ksp-analytics-sync"))
        assertTrue(executedTasks.contains("ksp-location-beacon"))

        engine.stop()
        assertEquals(EngineState.STOPPED, engine.state)
    }

    @Test
    fun `test reflection binding in android sample`() = runTest {
        val virtualTime = VirtualTimeSource(Instant.parse("2026-09-21T12:00:00Z"))
        val engine = ChronoTask.create(
            EngineConfig(clock = virtualTime.clock, timer = virtualTime.timer,
                dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        )

        val executedTasks = CopyOnWriteArrayList<String>()
        val tasks = ReflectionSampleTasks { taskId, _, _ ->
            executedTasks.add(taskId)
        }

        val handles = engine.registerAnnotated(tasks)
        assertEquals(1, handles.size)

        engine.start()
        virtualTime.advanceBy(2500L)
        testScheduler.advanceUntilIdle()

        assertTrue(executedTasks.contains("reflect-cache-evict"))

        engine.stop()
    }

    @Test
    fun `test kotlin dsl and task controls in android sample`() = runTest {
        val virtualTime = VirtualTimeSource(Instant.parse("2026-09-21T12:00:00Z"))
        val engine = ChronoTask.create(
            EngineConfig(clock = virtualTime.clock, timer = virtualTime.timer,
                dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        )

        val dslCounter = AtomicInteger(0)
        val handle = engine.schedule("dsl-heartbeat-task") {
            fixedRate(interval = 4.seconds, initialDelay = 1.seconds)
            execute {
                dslCounter.incrementAndGet()
            }
        }

        engine.start()
        assertEquals(TaskState.SCHEDULED, handle.state)

        // Trigger manual execution
        val result = handle.triggerNow()
        assertTrue(result is io.github.chronotask.execution.SubmissionResult.Started)
        assertEquals(1, dslCounter.get())

        // Pause task
        handle.pause()
        assertEquals(TaskState.PAUSED, handle.state)

        // Resume task
        handle.resume()
        assertEquals(TaskState.SCHEDULED, handle.state)

        // Cancel task
        handle.cancel()
        assertEquals(TaskState.CANCELLED, handle.state)

        engine.stop()
    }
}
