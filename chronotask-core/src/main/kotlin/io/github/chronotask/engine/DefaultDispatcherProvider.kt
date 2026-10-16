package io.github.chronotask.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation of [DispatcherProvider] supporting named dispatchers.
 */
class DefaultDispatcherProvider @JvmOverloads constructor(
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialNamedDispatchers: Map<String, CoroutineDispatcher> = emptyMap()
) : DispatcherProvider {

    private val dispatchers = ConcurrentHashMap<String, CoroutineDispatcher>(initialNamedDispatchers)

    fun register(key: String, dispatcher: CoroutineDispatcher) {
        require(key.isNotBlank()) { "Dispatcher key must not be blank" }
        dispatchers[key] = dispatcher
    }

    override fun get(key: String?): CoroutineDispatcher {
        if (key == null) return defaultDispatcher
        return dispatchers[key] ?: throw IllegalArgumentException("Unknown dispatcherKey: '$key'")
    }
}
