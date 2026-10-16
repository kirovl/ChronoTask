package io.github.chronotask.engine

import io.github.chronotask.concurrency.TaskConcurrencyState
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskState
import io.github.chronotask.execution.ExecutionId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Internal mutable runtime holder for a registered task.
 */
internal class TaskRuntime(
    val runtimeId: Long,
    val definition: TaskDefinition,
    val registeredAt: Instant
) {
    val taskLock = Any()
    val concurrencyState = TaskConcurrencyState(definition.id)

    var taskPaused: Boolean = !definition.isEnabled
    var taskCancelled: Boolean = false
    var lastScheduledAt: Instant? = null
    var lastStartedAt: Instant? = null
    var lastCompletedAt: Instant? = null
    var nextScheduledAt: Instant? = null
    var activeExecutions: Int = 0
    val startedExecutionIds = mutableSetOf<ExecutionId>()
    var scheduleCompleted: Boolean = false
    var scheduleFailure: Throwable? = null

    var currentSchedulerJob: Job? = null
    var schedulerGeneration: Long = 0L

    var completionDeferred: CompletableDeferred<Unit>? = null
    var completionExecutionId: ExecutionId? = null

    fun computeState(engineState: EngineState): TaskState = synchronized(taskLock) {
        when {
            taskCancelled -> TaskState.CANCELLED
            taskPaused || engineState == EngineState.PAUSED -> TaskState.PAUSED
            activeExecutions > 0 -> TaskState.RUNNING
            engineState == EngineState.CREATED -> TaskState.REGISTERED
            else -> TaskState.SCHEDULED
        }
    }

    fun snapshot(engineState: EngineState): TaskRuntimeSnapshot = synchronized(taskLock) {
        TaskRuntimeSnapshot(
            taskId = definition.id,
            state = computeState(engineState),
            registeredAt = registeredAt,
            lastScheduledAt = lastScheduledAt,
            lastStartedAt = lastStartedAt,
            lastCompletedAt = lastCompletedAt,
            nextScheduledAt = nextScheduledAt,
            activeExecutions = activeExecutions,
            scheduleCompleted = scheduleCompleted,
            scheduleFailure = scheduleFailure
        )
    }

    companion object {
        private val ID_GENERATOR = AtomicLong(0L)

        fun create(definition: TaskDefinition, registeredAt: Instant): TaskRuntime {
            return TaskRuntime(
                runtimeId = ID_GENERATOR.incrementAndGet(),
                definition = definition,
                registeredAt = registeredAt
            )
        }
    }
}
