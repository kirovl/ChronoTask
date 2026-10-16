package io.github.chronotask.execution

/**
 * Outcome of executing a [TaskExecution].
 */
sealed interface ExecutionResult {

    /**
     * Execution completed successfully without throwing exceptions.
     */
    data object Success : ExecutionResult

    /**
     * Execution failed due to an uncaught [Throwable] (normally [Exception]).
     */
    data class Failed(
        val error: Throwable
    ) : ExecutionResult

    /**
     * Execution was cancelled via coroutine cancellation.
     */
    data object Cancelled : ExecutionResult

    /**
     * Execution was skipped according to concurrency policy.
     */
    data class Skipped(
        val reason: SkipReason
    ) : ExecutionResult

    companion object {
        @JvmField
        val SUCCESS: ExecutionResult = Success

        @JvmField
        val CANCELLED: ExecutionResult = Cancelled

        @JvmStatic
        fun failed(error: Throwable): ExecutionResult = Failed(error)

        @JvmStatic
        fun skipped(reason: SkipReason): ExecutionResult = Skipped(reason)
    }
}
