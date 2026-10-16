package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.SkipReason
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class QueuedDefinitionTest {
    enum class Policy(val value: ConcurrencyPolicy) {
        QUEUE_ONE(ConcurrencyPolicy.QueueOne),
        SERIAL(ConcurrencyPolicy.serial(1, QueueOverflowPolicy.DROP_OLDEST))
    }

    private fun execution() = TaskExecution(
        taskId = "same-id",
        trigger = ScheduledTrigger(Instant.EPOCH, Instant.EPOCH, TriggerSource.MANUAL)
    )

    @ParameterizedTest
    @EnumSource(Policy::class)
    fun `queued work runs its submitted definition`(policy: Policy) = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val started = mutableMapOf<ExecutionId, TaskDefinition>()
        val succeeded = mutableMapOf<ExecutionId, TaskDefinition>()
        val release = CompletableDeferred<Unit>()
        val listener = object : ExecutionListener {
            override fun onStarted(definition: TaskDefinition, execution: TaskExecution) {
                started[execution.executionId] = definition
            }
            override fun onSuccess(definition: TaskDefinition, execution: TaskExecution) {
                succeeded[execution.executionId] = definition
            }
        }
        val first = TaskDefinition(
            id = "same-id", schedule = TaskSchedule.Once(), concurrencyPolicy = policy.value,
            executable = { calls += "first"; release.await() }
        )
        val second = first.copy(name = "second", executable = { calls += "second" })
        val third = first.copy(name = "third", executable = { calls += "third" })
        val queued = execution()
        val later = execution()
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Unconfined, listener = listener)
        try {
            assertTrue(controller.submit(first, execution()) is SubmissionResult.Started)
            assertTrue(controller.submit(second, queued) is SubmissionResult.Queued)
            release.complete(Unit)
            assertTrue(controller.submit(third, later) is SubmissionResult.Started)
            assertEquals(listOf("first", "second", "third"), calls)
            assertSame(second, started[queued.executionId])
            assertSame(second, succeeded[queued.executionId])
            assertSame(third, started[later.executionId])
        } finally {
            controller.close()
        }
    }

    @ParameterizedTest
    @EnumSource(Policy::class)
    fun `replacement callback retains replaced definition`(policy: Policy) = runBlocking<Unit> {
        val skipped = mutableListOf<Triple<TaskDefinition, TaskExecution, SkipReason>>()
        val calls = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val listener = object : ExecutionListener {
            override fun onSkipped(definition: TaskDefinition, execution: TaskExecution, reason: SkipReason) {
                skipped += Triple(definition, execution, reason)
            }
        }
        val first = TaskDefinition(
            id = "same-id", schedule = TaskSchedule.Once(), concurrencyPolicy = policy.value,
            executable = { release.await() }
        )
        val second = first.copy(name = "second", executable = { calls += "second" })
        val third = first.copy(name = "third", executable = { calls += "third" })
        val replaced = execution()
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Unconfined, listener = listener)
        try {
            controller.submit(first, execution())
            controller.submit(second, replaced)
            controller.submit(third, execution())
            assertEquals(listOf(Triple(second, replaced, SkipReason.QUEUE_REPLACED)), skipped)
            assertSame(second, skipped.single().first)
            release.complete(Unit)
            assertEquals(listOf("third"), calls)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `individual and bulk cancellation retain each queued definition`() = runBlocking<Unit> {
        val cancelled = mutableMapOf<ExecutionId, TaskDefinition>()
        val calls = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val listener = object : ExecutionListener {
            override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {
                cancelled[execution.executionId] = definition
            }
        }
        val first = TaskDefinition(
            id = "same-id", schedule = TaskSchedule.Once(), concurrencyPolicy = ConcurrencyPolicy.serial(4),
            executable = { release.await() }
        )
        val second = first.copy(name = "second", executable = { calls += "second" })
        val third = first.copy(name = "third", executable = { calls += "third" })
        val fourth = first.copy(name = "fourth", executable = { calls += "fourth" })
        val running = execution()
        val individual = execution()
        val bulk = execution()
        val latest = execution()
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Unconfined, listener = listener)
        try {
            controller.submit(first, running)
            controller.submit(second, individual)
            controller.submit(third, bulk)
            controller.submit(fourth, latest)
            assertTrue(controller.cancelExecution(first.id, individual.executionId))
            assertSame(second, cancelled[individual.executionId])
            assertEquals(3, controller.cancelAll(first.id))
            assertSame(first, cancelled[running.executionId])
            assertSame(third, cancelled[bulk.executionId])
            assertSame(fourth, cancelled[latest.executionId])
            assertEquals(4, cancelled.size)
            assertEquals(0, controller.queuedCount(first.id))
            release.complete(Unit)
            assertTrue(calls.isEmpty())
        } finally {
            controller.close()
        }
    }
}
