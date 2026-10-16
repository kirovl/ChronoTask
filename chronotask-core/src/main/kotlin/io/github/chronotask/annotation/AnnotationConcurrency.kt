package io.github.chronotask.annotation

/**
 * Concurrency execution policy configuration for annotation-based tasks.
 */
enum class AnnotationConcurrency {
    /**
     * Triggers execute concurrently without restriction.
     */
    ALLOW,

    /**
     * Skips new trigger if an execution is already running.
     */
    SKIP_IF_RUNNING,

    /**
     * Holds at most one pending execution in queue; subsequent triggers replace it.
     */
    QUEUE_ONE,

    /**
     * Executes sequentially with a FIFO bounded queue.
     */
    SERIAL
}
