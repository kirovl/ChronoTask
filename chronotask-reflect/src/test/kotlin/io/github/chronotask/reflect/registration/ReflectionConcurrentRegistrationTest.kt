package io.github.chronotask.reflect.registration

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.reflect.registerAnnotated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ReflectionConcurrentRegistrationTest {

    private lateinit var engine: ChronoTaskEngine

    @BeforeEach
    fun setUp() {
        engine = ChronoTask.create()
        engine.start()
    }

    @AfterEach
    fun tearDown() {
        engine.stop()
    }

    class W0 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W1 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W2 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W3 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W4 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W5 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W6 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W7 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W8 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W9 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W10 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W11 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W12 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W13 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W14 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W15 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W16 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W17 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W18 { @Scheduled(intervalMs = 100_000) fun run() {} }
    class W19 { @Scheduled(intervalMs = 100_000) fun run() {} }

    @Test
    fun `concurrent registration of distinct instances succeeds without race conditions`() = runBlocking {
        val workers = listOf(
            W0(), W1(), W2(), W3(), W4(), W5(), W6(), W7(), W8(), W9(),
            W10(), W11(), W12(), W13(), W14(), W15(), W16(), W17(), W18(), W19()
        )

        val handles = workers.map { worker ->
            async(Dispatchers.Default) {
                engine.registerAnnotated(worker)
            }
        }.awaitAll().flatten()

        assertEquals(workers.size, handles.size)
        handles.forEach { handle ->
            assertNotNull(engine.getHandle(handle.taskId))
        }
    }

    @Test
    fun `concurrent registration of duplicate ids produces fail-fast for duplicates and registers winner`() = runBlocking {
        class DuplicateTarget {
            @Scheduled(id = "shared-duplicate-id", intervalMs = 100_000)
            fun task() {}
        }

        val attempts = 20
        val targets = (0 until attempts).map { DuplicateTarget() }

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        targets.map { target ->
            async(Dispatchers.Default) {
                try {
                    val handles = engine.registerAnnotated(target)
                    if (handles.isNotEmpty()) {
                        successCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, successCount.get())
        assertEquals(attempts - 1, failureCount.get())
        assertNotNull(engine.getHandle("shared-duplicate-id"))
    }
}
