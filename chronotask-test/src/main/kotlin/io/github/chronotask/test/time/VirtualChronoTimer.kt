package io.github.chronotask.test.time

import io.github.chronotask.clock.ChronoTimer
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Test implementation of [ChronoTimer] backed by a [VirtualTimeSource].
 */
class VirtualChronoTimer @JvmOverloads constructor(
    val timeSource: VirtualTimeSource = VirtualTimeSource()
) : ChronoTimer {

    override suspend fun delay(millis: Long) {
        require(millis >= 0) { "Delay duration must be non-negative: $millis" }
        if (millis == 0L) return
        suspendCancellableCoroutine { cont ->
            timeSource.registerWaiter(millis, cont)
        }
    }
}
