package io.github.chronotask.concurrency

import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class ConcurrencyPolicyChangeTest {
    enum class Policy(val value: ConcurrencyPolicy) {
        ALLOW(ConcurrencyPolicy.Allow),
        SKIP(ConcurrencyPolicy.SkipIfRunning),
        QUEUE_ONE(ConcurrencyPolicy.QueueOne),
        SERIAL(ConcurrencyPolicy.serial(4))
    }

    private fun execution() = TaskExecution(
        taskId = "policy-test",
        trigger = ScheduledTrigger(Instant.EPOCH, Instant.EPOCH, TriggerSource.MANUAL)
    )

    @ParameterizedTest
    @EnumSource(Policy::class)
    fun `policy changes fail before accepting work and preserve original state`(policy: Policy) = runBlocking<Unit> {
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val definition = TaskDefinition(
            id = "policy-test", schedule = TaskSchedule.Once(), concurrencyPolicy = policy.value,
            executable = { calls += "first"; release.await() }
        )
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Unconfined)
        try {
            assertTrue(controller.submit(definition, execution()) is SubmissionResult.Started)
            for (other in Policy.entries.filter { it != policy }) {
                val changed = definition.copy(concurrencyPolicy = other.value, executable = { calls += "unexpected" })
                val error = assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { controller.submit(changed, execution()) }
                }
                assertTrue(error.message!!.contains(definition.id))
                assertEquals(1, controller.activeCount(definition.id))
                assertEquals(0, controller.queuedCount(definition.id))
            }
            // A different definition with the same policy still runs its own body.
            val next = definition.copy(executable = { calls += "next" })
            val submission = controller.submit(next, execution())
            when (policy) {
                Policy.ALLOW -> assertTrue(submission is SubmissionResult.Started)
                Policy.SKIP -> assertTrue(submission is SubmissionResult.Skipped)
                Policy.QUEUE_ONE, Policy.SERIAL -> assertTrue(submission is SubmissionResult.Queued)
            }
            release.complete(Unit)
            assertEquals(if (policy == Policy.SKIP) listOf("first") else listOf("first", "next"), calls)
            assertEquals(0, controller.queuedCount(definition.id))
            assertEquals(0, controller.activeCount(definition.id))

            // Even an idle state keeps its policy, until explicitly retired.
            val changed = next.copy(concurrencyPolicy = Policy.entries.first { it != policy }.value)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { controller.submit(changed, execution()) }
            }
            assertEquals(0, controller.cancelAll(definition.id))
            assertTrue(controller.submit(changed, execution()) is SubmissionResult.Started)
            assertEquals("next", calls.last())
        } finally {
            controller.close()
        }
    }

    @Test
    fun `serial capacity and overflow changes require retiring the state`() = runBlocking<Unit> {
        val release = CompletableDeferred<Unit>()
        val definition = TaskDefinition(
            id = "policy-test", schedule = TaskSchedule.Once(), concurrencyPolicy = ConcurrencyPolicy.serial(4),
            executable = { release.await() }
        )
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Unconfined)
        try {
            controller.submit(definition, execution())
            controller.submit(definition, execution())
            for (policy in listOf(ConcurrencyPolicy.serial(1), ConcurrencyPolicy.serial(4, QueueOverflowPolicy.REJECT))) {
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { controller.submit(definition.copy(concurrencyPolicy = policy), execution()) }
                }
                assertEquals(1, controller.queuedCount(definition.id))
            }
            assertEquals(2, controller.cancelAll(definition.id))
        } finally {
            controller.close()
        }
    }
}
