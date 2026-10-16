package io.github.chronotask.engine

import io.github.chronotask.model.TaskState
import java.time.Instant

/**
 * Immutable point-in-time snapshot of a task's runtime status.
 *
 * @property taskId The unique task identifier.
 * @property state The current lifecycle state of the task.
 * @property registeredAt The timestamp the task was registered into the engine.
 * @property lastScheduledAt The previous planned trigger timestamp on the timeline.
 * @property lastStartedAt The timestamp the task executable was last started.
 * @property lastCompletedAt The timestamp the task executable was last completed.
 * @property nextScheduledAt The upcoming planned trigger timestamp, or null if schedule is completed/uncomputed.
 * @property activeExecutions The count of currently active executions.
 * @property scheduleCompleted True if the schedule has completed and will not produce further planned triggers.
 * @property scheduleFailure Any unhandled exception that halted the task's schedule loop, or null.
 */
data class TaskRuntimeSnapshot @JvmOverloads constructor(
    val taskId: String,
    val state: TaskState,
    val registeredAt: Instant,
    val lastScheduledAt: Instant?,
    val lastStartedAt: Instant?,
    val lastCompletedAt: Instant?,
    val nextScheduledAt: Instant?,
    val activeExecutions: Int,
    val scheduleCompleted: Boolean,
    val scheduleFailure: Throwable? = null
)
