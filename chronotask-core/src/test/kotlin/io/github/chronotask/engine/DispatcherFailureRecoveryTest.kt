package io.github.chronotask.engine

import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class DispatcherFailureRecoveryTest {
    enum class Policy(val value: ConcurrencyPolicy) {
        ALLOW(ConcurrencyPolicy.Allow), SKIP(ConcurrencyPolicy.SkipIfRunning),
        QUEUE_ONE(ConcurrencyPolicy.QueueOne), SERIAL(ConcurrencyPolicy.Serial(2048))
    }
    enum class QueuePolicy(val value: ConcurrencyPolicy, val failures: Int) {
        QUEUE_ONE(ConcurrencyPolicy.QueueOne, 1), SERIAL(ConcurrencyPolicy.Serial(2048), 1500)
    }
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @ParameterizedTest
    @EnumSource(Policy::class)
    fun `dispatcher recovery permits new triggers after failed launch`(policy: Policy) {
        val time = VirtualTimeSource(base)
        var lookups = 0
        var calls = 0
        val error = IllegalStateException("dispatcher unavailable")
        val provider = object : DispatcherProvider {
            override fun get(key: String?): CoroutineDispatcher {
                if (key != null && ++lookups == 2) throw error
                return Dispatchers.Unconfined
            }
        }
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = time.timer, dispatcherProvider = provider
        )).use { engine ->
            val handle = engine.register(TaskDefinition("dispatcher", schedule = TaskSchedule.Once(),
                dispatcherKey = "flaky", concurrencyPolicy = policy.value, executable = { calls++ }))
            engine.start()
            assertSame(error, handle.snapshot().scheduleFailure)
            assertTrue(handle.snapshot().scheduleCompleted)
            assertTrue(handle.triggerNow() is SubmissionResult.Started)
            assertEquals(1, calls)
            assertEquals(0, handle.snapshot().activeExecutions)
        }
    }

    @ParameterizedTest
    @EnumSource(QueuePolicy::class)
    fun `queued launch failures notify once and release all reservations`(policy: QueuePolicy) {
        val time = VirtualTimeSource(base)
        val gate = CompletableDeferred<Unit>()
        var unavailable = false
        var calls = 0
        val failures = mutableListOf<TaskExecution>()
        val error = IllegalStateException("dispatcher unavailable")
        val provider = object : DispatcherProvider {
            override fun get(key: String?): CoroutineDispatcher {
                if (key != null && unavailable) throw error
                return Dispatchers.Unconfined
            }
        }
        val listener = object : ExecutionListener {
            override fun onFailure(definition: TaskDefinition, execution: TaskExecution, failure: Throwable) {
                assertSame(error, failure)
                failures += execution
            }
        }
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = time.timer, dispatcherProvider = provider,
            executionListener = listener
        )).use { engine ->
            val handle = engine.register(TaskDefinition("queued-dispatcher", schedule = TaskSchedule.Once(),
                dispatcherKey = "flaky", concurrencyPolicy = policy.value,
                executable = { calls++; gate.await() }))
            assertTrue(handle.triggerNow() is SubmissionResult.Started)
            val queued = (1..1500).map { handle.triggerNow() }
            assertTrue(queued.all { it is SubmissionResult.Queued })
            unavailable = true
            gate.complete(Unit)
            assertEquals(policy.failures, failures.size)
            assertEquals(policy.failures, failures.map { it.executionId }.toSet().size)
            assertEquals(0, handle.snapshot().activeExecutions)
            unavailable = false
            assertTrue(handle.triggerNow() is SubmissionResult.Started)
            assertEquals(2, calls)
            assertEquals(0, handle.snapshot().activeExecutions)
        }
    }
}
