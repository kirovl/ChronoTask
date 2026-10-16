package io.github.chronotask.execution

/**
 * Reason when a task execution submission is rejected by the concurrency controller.
 */
enum class RejectionReason {
    /**
     * Rejected because the execution queue is at maximum capacity ([io.github.chronotask.model.QueueOverflowPolicy.REJECT]).
     */
    QUEUE_FULL,

    /**
     * Rejected because the concurrency controller is closed.
     */
    CONTROLLER_CLOSED
}
