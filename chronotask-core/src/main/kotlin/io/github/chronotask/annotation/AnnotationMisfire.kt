package io.github.chronotask.annotation

/**
 * Misfire policy configuration for annotation-based tasks.
 */
enum class AnnotationMisfire {
    /**
     * Skips all missed triggers and waits for the next scheduled occurrence.
     */
    SKIP,

    /**
     * Dispatches a single compensatory execution for missed triggers immediately.
     */
    FIRE_ONCE,

    /**
     * Catches up and executes missed triggers up to a specified limit.
     */
    CATCH_UP
}
