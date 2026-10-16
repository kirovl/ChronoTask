package io.github.chronotask.clock

import java.time.Duration
import java.time.Instant
import kotlin.time.Duration as KotlinDuration

/**
 * Abstraction for asynchronous non-blocking waiting in coroutines.
 *
 * Decouples task execution timing from real-world wall-clock delays, allowing
 * instant progression in tests via virtual time simulation.
 */
interface ChronoTimer {

    /**
     * Suspends the coroutine for the specified number of [millis].
     *
     * @param millis Delay duration in milliseconds. Must be non-negative.
     * @throws IllegalArgumentException if [millis] is negative.
     */
    suspend fun delay(millis: Long)

    /**
     * Suspends the coroutine for the specified Java [Duration].
     *
     * @param duration Delay duration. Must be non-negative.
     * @throws IllegalArgumentException if duration is negative.
     */
    suspend fun delay(duration: Duration) {
        require(!duration.isNegative) { "Delay duration must be non-negative: $duration" }
        val millis = duration.toMillis()
        require(millis >= 0) { "Delay duration must be non-negative: $duration" }
        delay(millis)
    }

    /**
     * Suspends the coroutine for the specified Kotlin [KotlinDuration].
     *
     * @param duration Delay duration. Must be non-negative.
     * @throws IllegalArgumentException if duration is negative.
     */
    suspend fun delay(duration: KotlinDuration) {
        require(!duration.isNegative()) { "Delay duration must be non-negative: $duration" }
        val millis = duration.inWholeMilliseconds
        require(millis >= 0) { "Delay duration must be non-negative: $duration" }
        delay(millis)
    }

    /**
     * Suspends the coroutine until [targetInstant] is reached according to [clock].
     *
     * If [targetInstant] is in the past or equal to [ChronoClock.now], returns immediately without suspending.
     *
     * @param targetInstant The planned target instant to wait for.
     * @param clock The clock used to determine current time. Defaults to [ChronoClock.SYSTEM].
     */
    suspend fun sleepUntil(targetInstant: Instant, clock: ChronoClock = ChronoClock.SYSTEM) {
        while (true) {
            val now = clock.now()
            if (!now.isBefore(targetInstant)) return
            val remaining = Duration.between(now, targetInstant)
            val wholeMillis = remaining.toMillis()
            // Millisecond timers must round up so the remaining fraction is not lost.
            val waitMillis = if (remaining.nano % 1_000_000 == 0) wholeMillis
                else Math.addExact(wholeMillis, 1)
            delay(waitMillis)
            // A timer can wake early, or the wall clock can move backwards while waiting.
        }
    }

    companion object {
        /**
         * The default system timer implementation delegating to [DefaultChronoTimer].
         */
        @JvmField
        val SYSTEM: ChronoTimer = DefaultChronoTimer
    }
}
