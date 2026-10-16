package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

class HighConcurrencyRegistrationStressTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun test1000TasksConcurrentRegistration() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Default)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val taskCount = 1000
        val deferreds = (1..taskCount).map { i ->
            async(Dispatchers.Default) {
                val def = TaskDefinition(
                    id = "concurrent-task-$i",
                    schedule = TaskSchedule.FixedRate(10_000L),
                    executable = { }
                )
                engine.register(def)
            }
        }

        val handles = deferreds.awaitAll()
        assertEquals(taskCount, handles.size)

        // Verify all 1000 tasks are present in registry
        for (i in 1..taskCount) {
            val handle = engine.getHandle("concurrent-task-$i")
            assertNotNull(handle, "Task concurrent-task-$i must exist in engine")
            assertEquals("concurrent-task-$i", handle?.taskId)
        }

        engine.stop()
    }
}
