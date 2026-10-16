package io.github.chronotask.execution

/**
 * Explanatory reason when a [TaskExecution] is skipped by concurrency policies.
 */
enum class SkipReason {
    /**
     * Skipped because another execution of the task is currently running ([io.github.chronotask.model.ConcurrencyPolicy.SkipIfRunning]).
     */
    ALREADY_RUNNING,

    /**
     * Skipped because the task's execution queue is full ([io.github.chronotask.model.QueueOverflowPolicy.DROP_LATEST]).
     */
    QUEUE_FULL,

    /**
     * Skipped because a newer execution replaced it in the queue ([io.github.chronotask.model.ConcurrencyPolicy.QueueOne] or [io.github.chronotask.model.QueueOverflowPolicy.DROP_OLDEST]).
     */
    QUEUE_REPLACED,

    /**
     * Skipped because the task or execution was cancelled before it started.
     */
    TASK_CANCELLED
}
