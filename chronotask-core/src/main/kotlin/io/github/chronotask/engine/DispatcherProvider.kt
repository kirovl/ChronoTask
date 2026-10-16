package io.github.chronotask.engine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Resolves [CoroutineDispatcher] instances for task execution according to [io.github.chronotask.model.TaskDefinition.dispatcherKey].
 */
fun interface DispatcherProvider {

    /**
     * Resolves the dispatcher corresponding to [key].
     *
     * @param key The dispatcher key configured on the task, or null for the default dispatcher.
     * @return The resolved [CoroutineDispatcher].
     * @throws IllegalArgumentException if the specified key is unknown.
     */
    fun get(key: String?): CoroutineDispatcher
}
