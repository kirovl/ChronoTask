package io.github.chronotask.model

/**
 * Result status of an individual task execution instance.
 */
enum class ExecutionStatus {
    /** Execution is waiting in queue to run. */
    QUEUED,

    /** Execution is actively running. */
    RUNNING,

    /** Execution completed successfully without unhandled exceptions. */
    SUCCESS,

    /** Execution failed with an unhandled exception. */
    FAILED,

    /** Execution was cancelled before or during execution. */
    CANCELLED,

    /** Execution was skipped (e.g. by [ConcurrencyPolicy.SkipIfRunning]). */
    SKIPPED
}
