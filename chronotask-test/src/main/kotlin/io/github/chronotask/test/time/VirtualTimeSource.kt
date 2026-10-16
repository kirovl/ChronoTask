package io.github.chronotask.test.time

import kotlinx.coroutines.CancellableContinuation
import java.time.Duration
import java.time.Instant
import java.util.PriorityQueue
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Duration as KotlinDuration

/**
 * Shared virtual timeline coordinator for [FakeChronoClock] and [VirtualChronoTimer].
 *
 * Ensures that clock readings and timer delays advance on the exact same deterministic timeline.
 * Thread-safe for multi-coroutine test executions.
 */
class VirtualTimeSource @JvmOverloads constructor(
    initialInstant: Instant = DEFAULT_INITIAL_INSTANT
) {

    private val lock = Any()
    private var currentInstant: Instant = initialInstant
    private var sequence: Long = 0L
    private var timelineGeneration: Long = 0L
    private val waiters = PriorityQueue<Waiter>()

    /**
     * Paired [FakeChronoClock] bound to this time source.
     */
    val clock: FakeChronoClock by lazy { FakeChronoClock(this) }

    /**
     * Paired [VirtualChronoTimer] bound to this time source.
     */
    val timer: VirtualChronoTimer by lazy { VirtualChronoTimer(this) }

    /**
     * Returns the current virtual [Instant].
     */
    fun now(): Instant = synchronized(lock) { currentInstant }

    /**
     * Returns the current virtual time in epoch milliseconds.
     */
    fun currentTimeMillis(): Long = synchronized(lock) { currentInstant.toEpochMilli() }

    /**
     * Returns the count of pending coroutines waiting on this timer.
     */
    fun waiterCount(): Int = synchronized(lock) { waiters.size }

    /**
     * Checks if there are any active waiters.
     */
    fun hasWaiters(): Boolean = synchronized(lock) { waiters.isNotEmpty() }

    /**
     * Advances the virtual timeline by [millis] milliseconds, resuming all expired timer waiters.
     *
     * @param millis Milliseconds to advance. Must be non-negative.
     * @throws IllegalArgumentException if [millis] is negative.
     */
    fun advanceBy(millis: Long) {
        require(millis >= 0) { "Cannot advance time by negative duration: $millis ms" }
        if (millis == 0L) return
        val (target, generation) = synchronized(lock) {
            currentInstant.plusMillis(millis) to timelineGeneration
        }
        advanceTimeline(target, generation)
    }

    /**
     * Advances the virtual timeline by [duration], resuming all expired timer waiters.
     */
    fun advanceBy(duration: Duration) {
        require(!duration.isNegative) { "Cannot advance time by negative duration: $duration" }
        val millis = duration.toMillis()
        require(millis >= 0) { "Cannot advance time by negative duration: $duration" }
        advanceBy(millis)
    }

    /**
     * Advances the virtual timeline by Kotlin [duration], resuming all expired timer waiters.
     */
    fun advanceBy(duration: KotlinDuration) {
        require(!duration.isNegative()) { "Cannot advance time by negative duration: $duration" }
        val millis = duration.inWholeMilliseconds
        require(millis >= 0) { "Cannot advance time by negative duration: $duration" }
        advanceBy(millis)
    }

    /**
     * Advances the virtual timeline to [targetInstant], resuming all expired timer waiters in chronological order.
     *
     * @param targetInstant Target instant to advance to. Must not be before the current instant.
     * @throws IllegalArgumentException if [targetInstant] is before the current virtual instant.
     */
    fun advanceTo(targetInstant: Instant) {
        val generation = synchronized(lock) {
            require(!targetInstant.isBefore(currentInstant)) {
                "Cannot move virtual time backwards: current=$currentInstant, target=$targetInstant"
            }
            timelineGeneration
        }

        advanceTimeline(targetInstant, generation)
    }

    private fun advanceTimeline(targetInstant: Instant, generation: Long) {
        while (true) {
            val waiterToResume = synchronized(lock) {
                // A reset starts a new timeline; older advances must not consume its waiters.
                if (generation != timelineGeneration) return
                val next = waiters.peek()
                if (next != null && !next.triggerInstant.isAfter(targetInstant)) {
                    waiters.poll()
                    if (currentInstant.isBefore(next.triggerInstant)) {
                        currentInstant = next.triggerInstant
                    }
                    next
                } else {
                    // A resumed coroutine or another thread may have advanced further.
                    if (currentInstant.isBefore(targetInstant)) {
                        currentInstant = targetInstant
                    }
                    null
                }
            } ?: break

            if (waiterToResume.continuation.isActive) {
                waiterToResume.continuation.resume(Unit)
            }
        }
    }

    /**
     * Registers a coroutine continuation to be resumed when virtual time reaches `currentInstant + millis`.
     */
    internal fun registerWaiter(millis: Long, cont: CancellableContinuation<Unit>) {
        require(millis >= 0) { "Delay duration must be non-negative: $millis" }
        if (millis == 0L) {
            cont.resume(Unit)
            return
        }

        val waiter = synchronized(lock) {
            val triggerInstant = currentInstant.plusMillis(millis)
            val w = Waiter(sequence++, triggerInstant, cont)
            waiters.add(w)
            w
        }

        cont.invokeOnCancellation {
            synchronized(lock) {
                waiters.remove(waiter)
            }
        }
    }

    /**
     * Resets the virtual timeline to [newInstant], cancels pending waiters, and
     * invalidates in-progress advances from the previous timeline.
     */
    fun reset(newInstant: Instant = DEFAULT_INITIAL_INSTANT) {
        val pendingWaiters: List<Waiter> = synchronized(lock) {
            timelineGeneration++
            currentInstant = newInstant
            sequence = 0L
            val copy = waiters.toList()
            waiters.clear()
            copy
        }
        for (w in pendingWaiters) {
            if (w.continuation.isActive) {
                w.continuation.cancel(CancellationException("VirtualTimeSource was reset"))
            }
        }
    }

    private data class Waiter(
        val id: Long,
        val triggerInstant: Instant,
        val continuation: CancellableContinuation<Unit>
    ) : Comparable<Waiter> {
        override fun compareTo(other: Waiter): Int {
            val cmp = this.triggerInstant.compareTo(other.triggerInstant)
            return if (cmp != 0) cmp else this.id.compareTo(other.id)
        }
    }

    companion object {
        val DEFAULT_INITIAL_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
