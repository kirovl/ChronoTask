package io.github.chronotask.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConcurrencyPolicyTest {

    @Test
    fun `test ConcurrencyPolicy singletons`() {
        assertSame(ConcurrencyPolicy.Allow, ConcurrencyPolicy.ALLOW)
        assertSame(ConcurrencyPolicy.SkipIfRunning, ConcurrencyPolicy.SKIP_IF_RUNNING)
        assertSame(ConcurrencyPolicy.QueueOne, ConcurrencyPolicy.QUEUE_ONE)
    }

    @Test
    fun `test Serial validation`() {
        assertThrows<IllegalArgumentException> { ConcurrencyPolicy.Serial(0) }
        assertThrows<IllegalArgumentException> { ConcurrencyPolicy.Serial(-1) }

        val serialDefault = ConcurrencyPolicy.Serial()
        assertEquals(1024, serialDefault.maxQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_OLDEST, serialDefault.overflowPolicy)

        val serialCustom = ConcurrencyPolicy.Serial(50, QueueOverflowPolicy.DROP_LATEST)
        assertEquals(50, serialCustom.maxQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_LATEST, serialCustom.overflowPolicy)

        val serialStatic = ConcurrencyPolicy.serial(100, QueueOverflowPolicy.REJECT)
        assertEquals(100, serialStatic.maxQueueSize)
        assertEquals(QueueOverflowPolicy.REJECT, serialStatic.overflowPolicy)

        assertEquals(serialCustom, ConcurrencyPolicy.Serial(50, QueueOverflowPolicy.DROP_LATEST))
    }
}
