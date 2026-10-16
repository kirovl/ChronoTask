package io.github.chronotask.clock

import java.time.Instant

/**
 * Production implementation of [ChronoClock] delegating to system time APIs.
 */
object DefaultChronoClock : ChronoClock {

    override fun now(): Instant = Instant.now()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
