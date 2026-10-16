package io.github.chronotask.engine

import io.github.chronotask.execution.*
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class CustomRunnerFeedbackTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    enum class Outcome(val event: String) {
        SUCCESS("success"), FAILURE("failure"), CANCELLED("cancelled"), SKIPPED("skipped")
    }

    private fun verify(outcome: Outcome, emitsCallbacks: Boolean) {
        val time = VirtualTimeSource(base)
        val error = IllegalStateException("reported failure")
        val events = mutableListOf<String>()
        var calls = 0
        val result = when (outcome) {
            Outcome.SUCCESS -> ExecutionResult.Success
            Outcome.FAILURE -> ExecutionResult.Failed(error)
            Outcome.CANCELLED -> ExecutionResult.Cancelled
            Outcome.SKIPPED -> ExecutionResult.Skipped(SkipReason.TASK_CANCELLED)
        }
        val listener = object : ExecutionListener {
            override fun onStarted(definition: TaskDefinition, execution: TaskExecution) { events += "start" }
            override fun onSuccess(definition: TaskDefinition, execution: TaskExecution) { events += "success" }
            override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) { events += "cancelled" }
            override fun onSkipped(definition: TaskDefinition, execution: TaskExecution, reason: SkipReason) {
                assertEquals(SkipReason.TASK_CANCELLED, reason)
                events += "skipped"
            }
            override fun onFailure(definition: TaskDefinition, execution: TaskExecution, failure: Throwable) {
                assertSame(error, failure)
                events += "failure"
            }
        }
        val runner = object : TaskRunner {
            override suspend fun run(
                definition: TaskDefinition, execution: TaskExecution,
                errorHandler: TaskErrorHandler?, listener: ExecutionListener?
            ): ExecutionResult {
                if (emitsCallbacks) repeat(2) { listener?.onStarted(definition, execution) }
                definition.executable.execute(TaskExecutionContext(execution.taskId, execution.trigger))
                if (emitsCallbacks) repeat(2) {
                    when (outcome) {
                        Outcome.SUCCESS -> listener?.onSuccess(definition, execution)
                        Outcome.FAILURE -> listener?.onFailure(definition, execution, error)
                        Outcome.CANCELLED -> listener?.onCancelled(definition, execution)
                        Outcome.SKIPPED -> listener?.onSkipped(definition, execution, SkipReason.TASK_CANCELLED)
                    }
                }
                return result
            }
        }
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined),
            runner = runner, executionListener = listener
        )).use { engine ->
            val handle = engine.register(TaskDefinition(
                "runner-feedback", schedule = TaskSchedule.FixedDelay(1000),
                executable = { calls++; time.timer.delay(10) }
            ))
            engine.start()
            assertEquals(1, handle.snapshot().activeExecutions)
            assertEquals(listOf("start"), events)
            time.advanceBy(1010)
            assertEquals(2, calls, "Returned outcomes must release fixed-delay feedback")
            assertEquals(1, handle.snapshot().activeExecutions)
            time.advanceBy(10)
            assertEquals(0, handle.snapshot().activeExecutions)
            assertEquals(base.plusMillis(1020), handle.snapshot().lastCompletedAt)
            val terminal = outcome.event
            assertEquals(listOf("start", terminal, "start", terminal), events)
        }
    }

    @ParameterizedTest
    @EnumSource(Outcome::class)
    fun `silent runner preserves recurring feedback`(outcome: Outcome) = verify(outcome, false)

    @ParameterizedTest
    @EnumSource(Outcome::class)
    fun `runner callbacks and returned outcome are deduplicated`(outcome: Outcome) = verify(outcome, true)
}
