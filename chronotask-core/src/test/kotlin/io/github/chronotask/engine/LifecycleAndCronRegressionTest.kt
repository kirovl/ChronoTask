package io.github.chronotask.engine

import io.github.chronotask.clock.ChronoClock
import io.github.chronotask.clock.ChronoTimer
import io.github.chronotask.cron.CronTriggerCalculator
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.*
import io.github.chronotask.trigger.TriggerContext
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume

class LifecycleAndCronRegressionTest {
    @Test fun resumeDuringEnginePauseMustKeepScheduling() = verifyResumeDuringPause(false)
    @Test fun resumeDuringHandlePauseMustKeepScheduling() = verifyResumeDuringPause(true)

    @Test fun resumeDuringEnginePauseMustKeepEveryTaskScheduling() = verifyResumeDuringPause(false, 3)

    @Test fun pauseDuringSchedulerRestartMustKeepTheTaskPaused() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        var now = base
        var callback: (() -> Unit)? = null
        val pending = mutableListOf<CancellableContinuation<Unit>>()
        val timer = object : ChronoTimer {
            override suspend fun delay(millis: Long) = suspendCancellableCoroutine<Unit> { continuation ->
                pending += continuation
                continuation.invokeOnCancellation {
                    val action = callback
                    callback = null
                    action?.invoke()
                }
            }
        }
        var calls = 0
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = ChronoClock { now }, timer = timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            val handle = engine.register(TaskDefinition("task", schedule = TaskSchedule.FixedRate(1000, 1000),
                executable = TaskExecutable { calls++ }))
            engine.start()
            callback = { handle.pause() }
            handle.resume() // Restart the live scheduler; cancellation pauses the replacement.
            assertEquals(TaskState.PAUSED, handle.state)
            now = base.plusSeconds(1)
            pending.toList().filter { it.isActive }.forEach { it.resume(Unit) }
            assertEquals(0, calls)
            handle.resume()
            assertEquals(1, calls)
        } finally { callback = null; engine.stop() }
    }

    private fun verifyResumeDuringPause(perTask: Boolean, taskCount: Int = 1) {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        var now = base
        var callback: (() -> Unit)? = null
        val pending = mutableListOf<CancellableContinuation<Unit>>()
        val timer = object : ChronoTimer {
            override suspend fun delay(millis: Long) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    pending += continuation
                    continuation.invokeOnCancellation {
                        val action = callback
                        callback = null
                        action?.invoke()
                    }
                }
            }
        }
        var calls = 0
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = ChronoClock { now }, timer = timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            val handle = engine.register(TaskDefinition("task", "task", TaskSchedule.FixedRate(1000, 1000),
                executable = TaskExecutable { calls++ }))
            for (index in 1 until taskCount) {
                engine.register(TaskDefinition("other-$index", schedule = TaskSchedule.FixedRate(1000, 1000),
                    executable = TaskExecutable { calls++ }))
            }
            engine.start()
            callback = if (perTask) ({ handle.resume() }) else ({ engine.resume() })
            if (perTask) handle.pause() else engine.pause()
            assertEquals(EngineState.RUNNING, engine.state)
            assertNotEquals(TaskState.PAUSED, handle.state)
            now = base.plusSeconds(1)
            pending.toList().filter { it.isActive }.forEach { it.resume(Unit) }
            assertEquals(taskCount, calls, "A resumed task must retain its new scheduler after the old cancellation finishes")
        } finally { callback = null; engine.stop() }
    }

    @Test fun cronMustNotSkipSecondOccurrenceDuringHalfHourOverlap() {
        val zone = ZoneId.of("Australia/Lord_Howe")
        val first = Instant.parse("2026-04-04T14:45:00Z")
        val second = Instant.parse("2026-04-04T15:15:00Z")
        // Both instants map to 01:45, with offsets +11:00 and +10:30.
        assertEquals(first.atZone(zone).toLocalDateTime(), second.atZone(zone).toLocalDateTime())
        val calculator = CronTriggerCalculator(TaskSchedule.Cron("0 45 1 * * ?", zone))
        val result = calculator.nextTrigger(TriggerContext(now = first, registeredAt = first, lastScheduledAt = first, lastStartedAt = null, lastCompletedAt = null))
        assertEquals(second, result, "The second matching occurrence must be returned before the next day")
    }

    @Test fun cronMatchesChronologicalSearchAcrossHalfHourAndHourTransitions() {
        val transitions = mapOf(
            "Australia/Lord_Howe" to listOf("2026-04-04T15:00:00Z", "2026-10-03T15:30:00Z"),
            "America/New_York" to listOf("2026-03-08T07:00:00Z", "2026-11-01T06:00:00Z")
        )
        val expressions = listOf("0 */15 1,2 * * ?", "0 45 1 * * ?", "0 15 2 * * ?")
        for ((zoneName, instants) in transitions) {
            val zone = ZoneId.of(zoneName)
            for (instant in instants) {
                for (offset in listOf(-1801L, -1L, 0L, 1L, 1799L)) {
                    val base = Instant.parse(instant).plusSeconds(offset)
                    for (expression in expressions) {
                        val calculator = CronTriggerCalculator(TaskSchedule.Cron(expression, zone))
                        // Independent chronological search: all these expressions match whole minutes.
                        var expected = base.truncatedTo(ChronoUnit.MINUTES).plusSeconds(60)
                        val deadline = base.plusSeconds(172800)
                        while (!calculator.cron.matches(expected.atZone(zone)) && expected < deadline) {
                            expected = expected.plusSeconds(60)
                        }
                        assertTrue(expected < deadline)
                        val actual = calculator.nextTrigger(TriggerContext(base, base, base, null, null))
                        assertEquals(expected, actual, "zone=$zoneName, base=$base, expression=$expression")
                    }
                }
            }
        }
    }
}
