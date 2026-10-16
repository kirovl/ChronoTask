package io.github.chronotask.model

/**
 * Policy defining concurrency behavior when a task trigger fires while a previous execution is still running.
 */
sealed interface ConcurrencyPolicy {

    /**
     * Allows concurrent executions to run in parallel without restriction.
     */
    data object Allow : ConcurrencyPolicy

    /**
     * Skips new triggers if a previous execution of this task is currently active.
     */
    data object SkipIfRunning : ConcurrencyPolicy

    /**
     * Buffers at most one latest pending trigger to execute immediately after current run finishes.
     */
    data object QueueOne : ConcurrencyPolicy

    /**
     * Serializes all triggers sequentially in a FIFO queue with bounded capacity and overflow handling.
     *
     * @property maxQueueSize The maximum number of triggers that can be queued. Must be > 0.
     * @property overflowPolicy The action to take when the queue exceeds [maxQueueSize].
     */
    data class Serial @JvmOverloads constructor(
        val maxQueueSize: Int = 1024,
        val overflowPolicy: QueueOverflowPolicy = QueueOverflowPolicy.DROP_OLDEST
    ) : ConcurrencyPolicy {
        init {
            require(maxQueueSize > 0) { "maxQueueSize must be positive: $maxQueueSize" }
        }
    }

    companion object {
        @JvmField
        val ALLOW: ConcurrencyPolicy = Allow

        @JvmField
        val SKIP_IF_RUNNING: ConcurrencyPolicy = SkipIfRunning

        @JvmField
        val QUEUE_ONE: ConcurrencyPolicy = QueueOne

        @JvmField
        val SERIAL: ConcurrencyPolicy = Serial()

        @JvmStatic
        @JvmOverloads
        fun serial(
            maxQueueSize: Int = 1024,
            overflowPolicy: QueueOverflowPolicy = QueueOverflowPolicy.DROP_OLDEST
        ): Serial = Serial(maxQueueSize, overflowPolicy)
    }
}
