package io.github.chronotask.model

/**
 * Strategy applied when the FIFO queue capacity of [ConcurrencyPolicy.Serial] is exceeded.
 */
enum class QueueOverflowPolicy {
    /** Discard the oldest queued trigger to make room for the newly incoming trigger. */
    DROP_OLDEST,

    /** Discard the newly incoming trigger without queuing it. */
    DROP_LATEST,

    /** Reject the newly incoming trigger immediately. */
    REJECT
}
