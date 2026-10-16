package io.github.chronotask.sample.android

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.DefaultDispatcherProvider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.github.chronotask.model.EngineState
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.sample.android.tasks.ReflectionSampleTasks
import io.github.chronotask.test.time.VirtualTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidReflectionExecutionTest {

    @Test
    fun `test reflection registered task on virtual time in android`() = runTest {
        val virtualTime = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val engine = ChronoTask.create(
            EngineConfig(clock = virtualTime.clock, timer = virtualTime.timer,
                dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        )

        val executed = CopyOnWriteArrayList<String>()
        val tasks = ReflectionSampleTasks { id, _, _ ->
            executed.add(id)
        }

        val handles = engine.registerAnnotated(tasks)
        assertEquals(1, handles.size)

        engine.start()
        assertEquals(EngineState.RUNNING, engine.state)

        virtualTime.advanceBy(2500L)
        testScheduler.advanceUntilIdle()
        assertTrue(executed.contains("reflect-cache-evict"))

        engine.stop()
    }
}
