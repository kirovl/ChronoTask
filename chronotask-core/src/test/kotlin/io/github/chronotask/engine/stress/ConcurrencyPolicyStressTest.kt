package io.github.chronotask.engine.stress

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ConcurrencyPolicyStressTest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testAllowConcurrencyPolicyStress() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Default)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val activeConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val totalExecutions = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        val handle = engine.register(
            TaskDefinition(
                id = "allow-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 10_000L, initialDelayMs = 10_000L),
                concurrencyPolicy = ConcurrencyPolicy.ALLOW,
                executable = {
                    val cur = activeConcurrent.incrementAndGet()
                    maxConcurrent.accumulateAndGet(cur) { m, c -> maxOf(m, c) }
                    gate.await()
                    activeConcurrent.decrementAndGet()
                    totalExecutions.incrementAndGet()
                }
            )
        )

        // Trigger 50 concurrent manual triggers
        val jobs = (1..50).map {
            async(Dispatchers.Default) {
                handle.triggerNow()
            }
        }
        jobs.awaitAll()

        // Unblock all executions
        gate.complete(Unit)

        // Wait for all to finish
        while (totalExecutions.get() < 50) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(50, totalExecutions.get())
        assertTrue(maxConcurrent.get() > 1, "ALLOW should have executed multiple instances concurrently")

        engine.stop()
    }

    @Test
    fun testSkipIfRunningPolicyStress() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Default)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val totalExecuted = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        val handle = engine.register(
            TaskDefinition(
                id = "skip-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 10_000L, initialDelayMs = 10_000L),
                concurrencyPolicy = ConcurrencyPolicy.SKIP_IF_RUNNING,
                executable = {
                    totalExecuted.incrementAndGet()
                    gate.await()
                }
            )
        )

        // Fire first trigger
        handle.triggerNow()

        // Wait until task is running
        while (totalExecuted.get() < 1) {
            kotlinx.coroutines.delay(5)
        }

        // Fire 20 concurrent triggers while first is running -> all should be skipped
        val jobs = (1..20).map {
            async(Dispatchers.Default) {
                handle.triggerNow()
            }
        }
        jobs.awaitAll()

        gate.complete(Unit)
        kotlinx.coroutines.delay(50)

        assertEquals(1, totalExecuted.get(), "SKIP_IF_RUNNING should have skipped all overlapping triggers")

        engine.stop()
    }

    @Test
    fun testSerialBoundedQueueStress() = runBlocking {
        val timeSource = VirtualTimeSource(baseTime)
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Default)
        )
        val engine = ChronoTask.create(config)
        engine.start()

        val totalExecuted = AtomicInteger(0)
        val releaseGate = CompletableDeferred<Unit>()

        // Max queue size = 5, REJECT
        val handle = engine.register(
            TaskDefinition(
                id = "serial-task",
                schedule = TaskSchedule.FixedRate(intervalMs = 10_000L, initialDelayMs = 10_000L),
                concurrencyPolicy = ConcurrencyPolicy.Serial(maxQueueSize = 5, overflowPolicy = QueueOverflowPolicy.REJECT),
                executable = {
                    releaseGate.await()
                    totalExecuted.incrementAndGet()
                }
            )
        )

        // 1 running + 5 queued = 6 accepted; remaining rejected
        for (i in 1..20) {
            handle.triggerNow()
        }

        releaseGate.complete(Unit)

        // Wait for queued executions to finish
        while (totalExecuted.get() < 6) {
            kotlinx.coroutines.delay(10)
        }
        kotlinx.coroutines.delay(50)

        assertEquals(6, totalExecuted.get(), "Bounded queue should accept 1 running + 5 queued and reject the rest")

        engine.stop()
    }
}
