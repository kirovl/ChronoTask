package io.github.chronotask.test.time

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class VirtualChronoTimerTest {

    @Test
    fun `delay negative duration throws IllegalArgumentException`() = runBlocking<Unit> {
        val timer = VirtualChronoTimer()
        assertThrows<IllegalArgumentException> {
            timer.delay(-10L)
        }
    }

    @Test
    fun `delay zero completes immediately without registering waiter`() = runBlocking {
        val timeSource = VirtualTimeSource()
        val timer = timeSource.timer
        timer.delay(0L)
        assertEquals(0, timeSource.waiterCount())
    }

    @Test
    fun `single coroutine delay waits until virtual time advances`() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val timer = timeSource.timer

        val completed = AtomicBoolean(false)
        val job = launch(Dispatchers.Unconfined) {
            timer.delay(5000L)
            completed.set(true)
        }

        assertEquals(1, timeSource.waiterCount())
        assertFalse(completed.get())

        timeSource.advanceBy(4999L)
        assertFalse(completed.get())
        assertEquals(1, timeSource.waiterCount())

        timeSource.advanceBy(1L)
        assertTrue(completed.get())
        assertEquals(0, timeSource.waiterCount())

        job.join()
    }

    @Test
    fun `multiple coroutines resume in order of trigger timestamps`() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val timer = timeSource.timer
        val executionOrder = mutableListOf<String>()

        launch(Dispatchers.Unconfined) {
            timer.delay(3000L)
            executionOrder.add("B:3000")
        }

        launch(Dispatchers.Unconfined) {
            timer.delay(1000L)
            executionOrder.add("A:1000")
        }

        launch(Dispatchers.Unconfined) {
            timer.delay(5000L)
            executionOrder.add("C:5000")
        }

        assertEquals(3, timeSource.waiterCount())

        timeSource.advanceBy(2000L)
        assertEquals(listOf("A:1000"), executionOrder)
        assertEquals(2, timeSource.waiterCount())

        timeSource.advanceBy(4000L)
        assertEquals(listOf("A:1000", "B:3000", "C:5000"), executionOrder)
        assertEquals(0, timeSource.waiterCount())
    }

    @Test
    fun `cancelled coroutine is removed from waiter queue`() = runBlocking {
        val timeSource = VirtualTimeSource()
        val timer = timeSource.timer

        val job = launch(Dispatchers.Unconfined) {
            timer.delay(10000L)
        }

        assertEquals(1, timeSource.waiterCount())

        job.cancel()
        job.join()

        assertEquals(0, timeSource.waiterCount())
    }
}
