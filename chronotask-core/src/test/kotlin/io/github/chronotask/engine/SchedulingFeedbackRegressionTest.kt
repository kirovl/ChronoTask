package io.github.chronotask.engine

import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class SchedulingFeedbackRegressionTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    private fun engine(time: VirtualTimeSource) = ChronoTaskEngine.create(
        EngineConfig(time.clock, time.timer, DefaultDispatcherProvider(Dispatchers.Unconfined))
    )

    @Test
    fun `cron and custom catch-up drop excess triggers and resume normal timeline`() {
        val schedules = listOf(
            TaskSchedule.Cron("* * * * * ?", ZoneId.of("UTC")),
            TaskSchedule.Custom { context ->
                (context.lastScheduledAt ?: context.registeredAt).plusSeconds(1)
            }
        )
        for (schedule in schedules) {
            val time = VirtualTimeSource(base)
            val engine = engine(time)
            val missed = mutableListOf<Instant>()
            val regular = mutableListOf<Instant>()
            try {
                engine.register(TaskDefinition(
                    id = "catch-up",
                    schedule = schedule,
                    concurrencyPolicy = ConcurrencyPolicy.Allow,
                    misfirePolicy = MisfirePolicy.CatchUp(3),
                    executable = { context ->
                        if (context.trigger.source == TriggerSource.MISFIRE) {
                            missed.add(context.trigger.scheduledAt)
                        } else {
                            regular.add(context.trigger.scheduledAt)
                        }
                    }
                ))
                engine.start()
                engine.pause()
                time.advanceBy(20_500L)
                engine.resume()
                assertEquals((1L..3L).map(base::plusSeconds), missed)
                time.advanceBy(500L)
                assertEquals(listOf(base.plusSeconds(21)), regular)
                assertEquals(3, missed.size)
            } finally {
                engine.stop()
            }
        }
    }

    @Test
    fun `manual completion cannot release an unfinished scheduled fixed-delay execution`() {
        val time = VirtualTimeSource(base)
        val engine = engine(time)
        val scheduled = mutableListOf<Instant>()
        try {
            val handle = engine.register(TaskDefinition(
                id = "delay",
                schedule = TaskSchedule.FixedDelay(1_000L),
                concurrencyPolicy = ConcurrencyPolicy.Allow,
                executable = { context ->
                    if (context.trigger.source == TriggerSource.MANUAL) {
                        time.timer.delay(1_000L)
                    } else {
                        scheduled.add(context.trigger.scheduledAt)
                        time.timer.delay(3_000L)
                    }
                }
            ))
            engine.start()
            time.advanceBy(1_000L)
            handle.triggerNow()
            time.advanceBy(2_000L)
            assertEquals(listOf(base), scheduled)
            time.advanceBy(1_000L)
            assertEquals(listOf(base, base.plusSeconds(4)), scheduled)
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `a replaced queued fixed-delay trigger must not leave its scheduler waiting`() {
        val time = VirtualTimeSource(base)
        val engine = engine(time)
        val scheduled = mutableListOf<Instant>()
        try {
            val handle = engine.register(TaskDefinition(
                id = "queued-delay",
                schedule = TaskSchedule.FixedDelay(1_000L, 1_000L),
                concurrencyPolicy = ConcurrencyPolicy.QueueOne,
                executable = { context ->
                    if (context.trigger.source == TriggerSource.MANUAL) {
                        time.timer.delay(3_000L)
                    } else {
                        scheduled.add(context.trigger.scheduledAt)
                    }
                }
            ))
            handle.triggerNow()
            engine.start()
            time.advanceBy(1_000L) // Scheduled execution waits behind the manual execution.
            time.advanceBy(500L)
            handle.triggerNow() // Replaces the scheduled execution in the one-slot queue.
            time.advanceBy(1_500L)
            assertEquals(listOf(base.plusSeconds(2)), scheduled)
            time.advanceBy(1_000L)
            assertEquals(listOf(base.plusSeconds(2), base.plusSeconds(4)), scheduled)
        } finally {
            engine.stop()
        }
    }
}
