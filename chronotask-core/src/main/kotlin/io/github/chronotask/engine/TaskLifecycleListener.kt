package io.github.chronotask.engine

import io.github.chronotask.model.TaskDefinition

/**
 * Observer interface for monitoring task lifecycle transitions within the engine.
 */
interface TaskLifecycleListener {

    fun onRegistered(definition: TaskDefinition) {}
    fun onPaused(definition: TaskDefinition) {}
    fun onResumed(definition: TaskDefinition) {}
    fun onCancelled(definition: TaskDefinition) {}
    fun onUnregistered(definition: TaskDefinition) {}
}
