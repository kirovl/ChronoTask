package io.github.chronotask.trigger

import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class CustomTriggerProgressTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `repeated and backwards custom triggers fail for all misfire policies`() {
        for (next in listOf(base, base.minusSeconds(1))) {
            for (policy in listOf(MisfirePolicy.Skip, MisfirePolicy.FireOnce, MisfirePolicy.CatchUp(2))) {
                val schedule = TaskSchedule.Custom { next }
                assertThrows(TriggerCalculationException::class.java) {
                    TriggerService().evaluate(schedule, policy, base, lastScheduledAt = base, now = base)
                }
                assertThrows(TriggerCalculationException::class.java) {
                    TriggerService().computeNextScheduledAt(schedule, TriggerContext(base, base, base, null, null))
                }
            }
        }
    }

    @Test fun `non progressing calculator halts only its own schedule`() {
        val time = VirtualTimeSource(base)
        var badCalls = 0
        var calculations = 0
        var healthyCalls = 0
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)
        )).use { engine ->
            // The bound keeps a broken implementation from hanging the test process.
            val bad = engine.register(TaskDefinition("constant", schedule = TaskSchedule.Custom {
                if (++calculations <= 3) base else null
            }, executable = { badCalls++ }))
            engine.register(TaskDefinition("healthy", schedule = TaskSchedule.FixedRate(1000),
                executable = { healthyCalls++ }))
            engine.start()
            assertEquals(1, badCalls)
            assertEquals(2, calculations)
            assertTrue(bad.snapshot().scheduleCompleted)
            assertTrue(bad.snapshot().scheduleFailure is TriggerCalculationException)
            time.advanceBy(1000)
            assertEquals(2, healthyCalls)
            assertEquals(1, badCalls)
        }
    }

    @Test fun `custom schedules may finish or advance after the first trigger`() {
        val service = TriggerService()
        assertNull(service.computeNextScheduledAt(TaskSchedule.Custom { null }, TriggerContext(base, base, base, null, null)))
        assertEquals(base.plusNanos(1), service.computeNextScheduledAt(
            TaskSchedule.Custom { base.plusNanos(1) }, TriggerContext(base, base, base, null, null)))
        assertEquals(base, service.computeNextScheduledAt(TaskSchedule.Custom { base }, TriggerContext(base, base, null, null, null)))
    }
}
