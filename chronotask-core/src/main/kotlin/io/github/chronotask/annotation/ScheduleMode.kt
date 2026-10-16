package io.github.chronotask.annotation

/**
 * Operating mode for tasks scheduled with [Scheduled].
 */
enum class ScheduleMode {
    /**
     * Triggers periodically at fixed intervals regardless of task execution duration.
     */
    FIXED_RATE,

    /**
     * Triggers with a fixed delay between completion of one execution and start of the next.
     */
    FIXED_DELAY
}
