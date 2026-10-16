package io.github.chronotask.test.time

import io.github.chronotask.clock.ChronoClock
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration as KotlinDuration

/**
 * Test implementation of [ChronoClock] backed by a [VirtualTimeSource].
 */
class FakeChronoClock @JvmOverloads constructor(
    val timeSource: VirtualTimeSource = VirtualTimeSource()
) : ChronoClock {

    constructor(initialInstant: Instant) : this(VirtualTimeSource(initialInstant))

    override fun now(): Instant = timeSource.now()

    override fun currentTimeMillis(): Long = timeSource.currentTimeMillis()

    /**
     * Convenience method to advance virtual time by [millis].
     */
    fun advanceBy(millis: Long) = timeSource.advanceBy(millis)

    /**
     * Convenience method to advance virtual time by [duration].
     */
    fun advanceBy(duration: Duration) = timeSource.advanceBy(duration)

    /**
     * Convenience method to advance virtual time by Kotlin [duration].
     */
    fun advanceBy(duration: KotlinDuration) = timeSource.advanceBy(duration)

    /**
     * Convenience method to advance virtual time to [targetInstant].
     */
    fun advanceTo(targetInstant: Instant) = timeSource.advanceTo(targetInstant)
}
