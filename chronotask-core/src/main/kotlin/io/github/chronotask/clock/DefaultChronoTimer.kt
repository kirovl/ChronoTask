package io.github.chronotask.clock

import kotlinx.coroutines.delay

/**
 * Production implementation of [ChronoTimer] delegating to [kotlinx.coroutines.delay].
 */
object DefaultChronoTimer : ChronoTimer {

    override suspend fun delay(millis: Long) {
        require(millis >= 0) { "Delay duration must be non-negative: $millis" }
        if (millis == 0L) return
        kotlinx.coroutines.delay(millis)
    }
}
