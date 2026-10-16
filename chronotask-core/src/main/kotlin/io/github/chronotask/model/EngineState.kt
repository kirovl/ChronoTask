package io.github.chronotask.model

/**
 * Lifecycle states of the scheduling engine.
 */
enum class EngineState {
    /** Engine is instantiated but not yet started. */
    CREATED,

    /** Engine is actively running and managing scheduled tasks. */
    RUNNING,

    /** Engine is paused; scheduled tasks are temporarily suspended from firing. */
    PAUSED,

    /** Engine has been stopped and all tasks cancelled. */
    STOPPED
}
