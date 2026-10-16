package io.github.chronotask.execution

/**
 * Result returned when submitting a [TaskExecution] to a [io.github.chronotask.concurrency.ConcurrencyController].
 */
sealed interface SubmissionResult {

    /**
     * The execution was immediately started.
     */
    data class Started(
        val execution: TaskExecution
    ) : SubmissionResult

    /**
     * The execution was placed into the task's pending queue.
     */
    data class Queued(
        val execution: TaskExecution
    ) : SubmissionResult

    /**
     * The execution was skipped per concurrency policy.
     */
    data class Skipped(
        val execution: TaskExecution,
        val reason: SkipReason
    ) : SubmissionResult

    /**
     * The execution submission was rejected.
     */
    data class Rejected(
        val execution: TaskExecution,
        val reason: RejectionReason
    ) : SubmissionResult
}
