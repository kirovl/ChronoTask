package io.github.chronotask.clock

import java.time.Instant

/**
 * Abstraction for reading current time across ChronoTask components.
 *
 * Provides a unified time-reading contract for production (system wall-clock)
 * and test environments (virtual time control).
 */
fun interface ChronoClock {

    /**
     * Returns the current [Instant] on the clock timeline.
     */
    fun now(): Instant

    /**
     * Returns the current time in milliseconds since the Unix epoch.
     */
    fun currentTimeMillis(): Long = now().toEpochMilli()

    companion object {
        /**
         * The default system wall-clock implementation delegating to [DefaultChronoClock].
         */
        @JvmField
        val SYSTEM: ChronoClock = DefaultChronoClock
    }
}
