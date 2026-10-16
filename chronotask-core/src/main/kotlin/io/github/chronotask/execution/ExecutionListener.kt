package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition

/**
 * Observer interface for monitoring execution lifecycle transitions.
 *
 * Implementations must not throw uncaught exceptions; errors thrown in listener
 * callbacks are caught and ignored by the runner to protect execution integrity.
 */
interface ExecutionListener {

    /**
     * Called when an execution has been accepted and placed in the pending queue.
     */
    fun onQueued(definition: TaskDefinition, execution: TaskExecution) {}

    /**
     * Called when an execution has moved to the RUNNING state and is about to start.
     */
    fun onStarted(definition: TaskDefinition, execution: TaskExecution) {}

    /**
     * Called when an execution has completed successfully.
     */
    fun onSuccess(definition: TaskDefinition, execution: TaskExecution) {}

    /**
     * Called when an execution has failed with an unhandled [Throwable].
     */
    fun onFailure(definition: TaskDefinition, execution: TaskExecution, error: Throwable) {}

    /**
     * Called when an execution has been cancelled.
     */
    fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {}

    /**
     * Called when an execution has been skipped according to concurrency policy.
     */
    fun onSkipped(definition: TaskDefinition, execution: TaskExecution, reason: SkipReason) {}
}
