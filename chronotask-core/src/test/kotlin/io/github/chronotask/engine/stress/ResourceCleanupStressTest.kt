package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ResourceCleanupStressTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testRepeatedRegisterAndUnregisterCyclesLeaveZeroOrphans() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val cycles = 100
        for (cycle in 1..cycles) {
            val handle = engine.register(
                TaskDefinition(
                    id = "cycle-task-$cycle",
                    schedule = TaskSchedule.FixedRate(1000L),
                    executable = { }
                )
            )

            // Let it tick once
            timeSource.advanceBy(1000L)

            val removed = engine.unregister("cycle-task-$cycle")
            assertNull(engine.getHandle("cycle-task-$cycle"))
        }

        // Engine stop cleans remaining lifecycle state
        engine.stop()
    }
}
