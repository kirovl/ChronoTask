package io.github.chronotask.engine

import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class EngineRaceStressTest {

    @Test
    fun `100 concurrent start calls result in single transition to RUNNING`() = runBlocking {
        val engine = ChronoTaskEngine.create()
        (1..100).map {
            async(Dispatchers.Default) {
                engine.start()
            }
        }.awaitAll()

        assertEquals(EngineState.RUNNING, engine.state)
        engine.stop()
    }

    @Test
    fun `100 concurrent duplicate registrations result in exactly 1 success`() = runBlocking {
        val engine = ChronoTaskEngine.create()
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val def = TaskDefinition(
            id = "race-duplicate",
            name = "Race Duplicate",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = {}
        )

        (1..100).map {
            async(Dispatchers.Default) {
                try {
                    engine.register(def)
                    successCount.incrementAndGet()
                } catch (_: DuplicateTaskException) {
                    failureCount.incrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, successCount.get())
        assertEquals(99, failureCount.get())
        engine.stop()
    }

    @Test
    fun `high frequency pause resume race maintains consistency`() = runBlocking {
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition(
            id = "pause-resume-race",
            name = "Race Task",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = {}
        )
        engine.register(def)

        (1..50).map { i ->
            async(Dispatchers.Default) {
                if (i % 2 == 0) engine.pause() else engine.resume()
            }
        }.awaitAll()

        val finalState = engine.state
        assert(finalState == EngineState.RUNNING || finalState == EngineState.PAUSED)
        engine.stop()
    }
}
