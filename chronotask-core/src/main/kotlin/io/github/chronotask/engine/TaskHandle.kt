package io.github.chronotask.engine

import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.model.TaskState

/**
 * Handle returned upon registering a task, providing lifecycle control and status inspection for that specific task.
 */
interface TaskHandle {

    /**
     * Unique identifier of the managed task.
     */
    val taskId: String

    /**
     * Current lifecycle state of the task.
     */
    val state: TaskState

    /**
     * Pauses the task's schedule loop, preventing subsequent planned triggers from firing.
     * Running executions are not cancelled.
     */
    fun pause()

    /**
     * Resumes a paused task schedule.
     */
    fun resume()

    /**
     * Cancels the task permanently, stopping the schedule loop and cancelling all running and queued executions.
     */
    fun cancel()

    /**
     * Unregisters this specific registration and cancels its scheduler and executions.
     * A replacement with the same ID is never removed.
     *
     * @return true if this registration was removed, false if it is no longer registered.
     * @throws UnsupportedOperationException if a custom handle does not implement this operation.
     */
    fun unregister(): Boolean = throw UnsupportedOperationException(
        "This handle does not support registration-specific unregistration"
    )

    /**
     * Manually triggers an immediate execution of the task ([io.github.chronotask.trigger.TriggerSource.MANUAL]).
     * Subject to the task's [io.github.chronotask.model.ConcurrencyPolicy].
     */
    fun triggerNow(): SubmissionResult

    /**
     * Returns an immutable point-in-time snapshot of the task's runtime state.
     */
    fun snapshot(): TaskRuntimeSnapshot
}
