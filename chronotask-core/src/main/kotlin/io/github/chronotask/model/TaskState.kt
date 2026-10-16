package io.github.chronotask.model

/**
 * Lifecycle runtime states of a registered task.
 */
enum class TaskState {
    /** Task is registered into the engine. */
    REGISTERED,

    /** Task is actively scheduled to run on its defined trigger timeline. */
    SCHEDULED,

    /** Task currently has one or more executions actively running. */
    RUNNING,

    /** Task is temporarily paused from firing. */
    PAUSED,

    /** Task has been cancelled or unregistered. */
    CANCELLED
}
