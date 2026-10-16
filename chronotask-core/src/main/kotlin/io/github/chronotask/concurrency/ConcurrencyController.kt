package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.TaskDefinition

/**
 * Coordinates task execution submissions according to each task's [io.github.chronotask.model.ConcurrencyPolicy].
 */
interface ConcurrencyController : AutoCloseable {

    /**
     * Submits a [TaskExecution] for execution according to [definition]'s concurrency policy.
     *
     * @param definition The task definition.
     * @param execution The execution attempt.
     * @return [SubmissionResult] indicating whether the execution was started, queued, skipped, or rejected.
     * @throws IllegalArgumentException In [DefaultConcurrencyController], if an existing task ID
     * is submitted with a different concurrency policy. The policy remains fixed until [cancelAll]
     * retires the task's coordination state; this includes Serial capacity and overflow settings.
     */
    suspend fun submit(
        definition: TaskDefinition,
        execution: TaskExecution
    ): SubmissionResult

    /**
     * Cancels a specific execution for the given [taskId].
     *
     * @param taskId The task ID.
     * @param executionId The execution ID to cancel.
     * @return true if the execution was found and cancelled, false otherwise.
     */
    fun cancelExecution(taskId: String, executionId: ExecutionId): Boolean

    /**
     * Cancels all running and queued executions for the given [taskId].
     *
     * @param taskId The task ID.
     * @return Total count of cancelled running and queued executions.
     */
    fun cancelAll(taskId: String): Int

    /**
     * Returns the count of currently running executions for [taskId].
     */
    fun activeCount(taskId: String): Int

    /**
     * Returns the count of currently queued executions for [taskId].
     */
    fun queuedCount(taskId: String): Int

    /**
     * Closes the controller, cancelling all active/queued executions and rejecting subsequent submissions.
     */
    override fun close()
}
