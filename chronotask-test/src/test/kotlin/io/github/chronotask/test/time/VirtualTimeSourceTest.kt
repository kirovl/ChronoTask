package io.github.chronotask.test.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class VirtualTimeSourceTest {

    @Test
    fun `reentrant reset invalidates advances and preserves new timeline waiters`() = runBlocking {
        val testScope = this
        for (relative in listOf(false, true)) {
            val time = VirtualTimeSource()
            val initial = time.now()
            val resetInstant = initial.minusMillis(10_000)
            var newWaiter: Job? = null
            var newWaiterRan = false
            val first = launch(Dispatchers.Unconfined) {
                time.timer.delay(500L)
                time.reset(resetInstant)
                newWaiter = testScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    time.timer.delay(100L)
                    newWaiterRan = true
                }
            }
            val oldWaiter = launch(Dispatchers.Unconfined) { time.timer.delay(800L) }
            if (relative) time.advanceBy(1000L) else time.advanceTo(initial.plusMillis(1000))
            first.join()
            oldWaiter.join()
            assertTrue(oldWaiter.isCancelled)
            assertEquals(resetInstant, time.now())
            assertEquals(1, time.waiterCount())
            assertFalse(newWaiterRan)
            time.advanceBy(100L)
            newWaiter!!.join()
            assertTrue(newWaiterRan)
            assertEquals(resetInstant.plusMillis(100), time.now())
        }
    }

    @Test
    fun `concurrent reset prevents an old advance from updating the new timeline`() = runBlocking {
        val time = VirtualTimeSource()
        val initial = time.now()
        val resumed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val oldJob = launch(Dispatchers.Unconfined) {
            time.timer.delay(500L)
            resumed.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            val advance = executor.submit { time.advanceBy(1000L) }
            assertTrue(resumed.await(5, TimeUnit.SECONDS))
            val resetInstant = initial.minusMillis(10_000)
            time.reset(resetInstant)
            var newWaiterRan = false
            val newWaiter = launch(Dispatchers.Unconfined) {
                time.timer.delay(100L)
                newWaiterRan = true
            }
            release.countDown()
            advance.get(5, TimeUnit.SECONDS)
            oldJob.join()
            assertEquals(resetInstant, time.now())
            assertEquals(1, time.waiterCount())
            assertFalse(newWaiterRan)
            time.advanceBy(100L)
            newWaiter.join()
            assertTrue(newWaiterRan)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `default initial instant is 2026-01-01`() {
        val timeSource = VirtualTimeSource()
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), timeSource.now())
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), timeSource.currentTimeMillis())
        assertEquals(0, timeSource.waiterCount())
        assertFalse(timeSource.hasWaiters())
    }

    @Test
    fun `custom initial instant is respected`() {
        val customInstant = Instant.parse("2025-05-15T18:30:00Z")
        val timeSource = VirtualTimeSource(customInstant)
        assertEquals(customInstant, timeSource.now())
    }

    @Test
    fun `advanceBy increments virtual time correctly`() {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        timeSource.advanceBy(5000L)
        assertEquals(Instant.parse("2026-01-01T00:00:05Z"), timeSource.now())

        timeSource.advanceBy(Duration.ofMinutes(10))
        assertEquals(Instant.parse("2026-01-01T00:10:05Z"), timeSource.now())

        timeSource.advanceBy(15.seconds)
        assertEquals(Instant.parse("2026-01-01T00:10:20Z"), timeSource.now())
    }

    @Test
    fun `advanceTo advances time forward`() {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val target = Instant.parse("2026-01-02T12:00:00Z")
        timeSource.advanceTo(target)
        assertEquals(target, timeSource.now())
    }

    @Test
    fun `advanceTo backwards throws IllegalArgumentException`() {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-02T00:00:00Z"))
        val pastTarget = Instant.parse("2026-01-01T00:00:00Z")
        val ex = assertThrows<IllegalArgumentException> {
            timeSource.advanceTo(pastTarget)
        }
        assertTrue(ex.message!!.contains("Cannot move virtual time backwards"))
    }

    @Test
    fun `advanceBy negative throws IllegalArgumentException`() {
        val timeSource = VirtualTimeSource()
        assertThrows<IllegalArgumentException> {
            timeSource.advanceBy(-100L)
        }
        assertThrows<IllegalArgumentException> {
            timeSource.advanceBy(Duration.ofSeconds(-1))
        }
        assertThrows<IllegalArgumentException> {
            timeSource.advanceBy((-1).seconds)
        }
    }

    @Test
    fun `advanceBy rejects negative sub-millisecond durations`() {
        val timeSource = VirtualTimeSource()
        val initial = timeSource.now()
        assertThrows<IllegalArgumentException> {
            timeSource.advanceBy(Duration.ofNanos(-1))
        }
        assertThrows<IllegalArgumentException> {
            timeSource.advanceBy((-1).nanoseconds)
        }
        assertEquals(initial, timeSource.now())
    }

    @Test
    fun `reentrant advancement preserves later time and drains due waiters`() = runBlocking {
        val timeSource = VirtualTimeSource()
        val initial = timeSource.now()
        val observed = mutableListOf<Instant>()
        val first = launch(Dispatchers.Unconfined) {
            timeSource.timer.delay(500L)
            observed += timeSource.now()
            timeSource.advanceTo(initial.plusMillis(2000))
        }
        val second = launch(Dispatchers.Unconfined) {
            timeSource.timer.delay(1500L)
            observed += timeSource.now()
        }
        timeSource.advanceTo(initial.plusMillis(1000))
        first.join()
        second.join()
        assertEquals(2, observed.size)
        assertEquals(initial.plusMillis(500), observed[0])
        // Unconfined may defer the second continuation until the nested advance returns.
        assertTrue(!observed[1].isBefore(initial.plusMillis(1500)))
        assertTrue(!observed[1].isAfter(timeSource.now()))
        assertEquals(initial.plusMillis(2000), timeSource.now())
        assertEquals(0, timeSource.waiterCount())
    }

    @Test
    fun `concurrent advancement cannot overwrite later time`() = runBlocking {
        val timeSource = VirtualTimeSource()
        val initial = timeSource.now()
        val resumed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val job = launch(Dispatchers.Unconfined) {
            timeSource.timer.delay(500L)
            resumed.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            val earlierAdvance = executor.submit {
                timeSource.advanceTo(initial.plusMillis(1000))
            }
            assertTrue(resumed.await(5, TimeUnit.SECONDS))
            timeSource.advanceTo(initial.plusMillis(2000))
            release.countDown()
            earlierAdvance.get(5, TimeUnit.SECONDS)
            job.join()
            assertEquals(initial.plusMillis(2000), timeSource.now())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `reset clears state and updates instant`() {
        val timeSource = VirtualTimeSource()
        timeSource.advanceBy(10000L)
        val resetInstant = Instant.parse("2026-12-31T23:59:59Z")
        timeSource.reset(resetInstant)

        assertEquals(resetInstant, timeSource.now())
        assertEquals(0, timeSource.waiterCount())
    }
}
