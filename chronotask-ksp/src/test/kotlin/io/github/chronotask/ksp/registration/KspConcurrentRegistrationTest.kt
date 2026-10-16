package io.github.chronotask.ksp.registration

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
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

class CW0 { @Scheduled(intervalMs = 100_000) fun run() {} }
class CW1 { @Scheduled(intervalMs = 100_000) fun run() {} }
class CW2 { @Scheduled(intervalMs = 100_000) fun run() {} }
class CW3 { @Scheduled(intervalMs = 100_000) fun run() {} }
class CW4 { @Scheduled(intervalMs = 100_000) fun run() {} }

class DuplicateKspTarget {
    @Scheduled(id = "shared-duplicate-ksp-id", intervalMs = 100_000)
    fun task() {}
}

class KspConcurrentRegistrationTest {

    private val engine = ChronoTask.create()

    @BeforeEach
    fun setUp() {
        engine.start()
    }

    @AfterEach
    fun tearDown() {
        engine.stop()
    }

    @Test
    fun `concurrent registration of distinct KSP bindings succeeds without race conditions`() = runBlocking {
        val w0 = CW0()
        val w1 = CW1()
        val w2 = CW2()
        val w3 = CW3()
        val w4 = CW4()

        val handles = listOf(
            async(Dispatchers.Default) { CW0_ChronoTaskBinding.register(engine, w0) },
            async(Dispatchers.Default) { CW1_ChronoTaskBinding.register(engine, w1) },
            async(Dispatchers.Default) { CW2_ChronoTaskBinding.register(engine, w2) },
            async(Dispatchers.Default) { CW3_ChronoTaskBinding.register(engine, w3) },
            async(Dispatchers.Default) { CW4_ChronoTaskBinding.register(engine, w4) }
        ).awaitAll().flatten()

        assertEquals(5, handles.size)
        handles.forEach { handle ->
            assertNotNull(engine.getHandle(handle.taskId))
        }
    }

    @Test
    fun `concurrent registration of duplicate ids produces fail-fast for duplicates and registers winner`() = runBlocking {
        val attempts = 20
        val targets = (0 until attempts).map { DuplicateKspTarget() }

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        targets.map { target ->
            async(Dispatchers.Default) {
                try {
                    val handles = DuplicateKspTarget_ChronoTaskBinding.register(engine, target)
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
        assertNotNull(engine.getHandle("shared-duplicate-ksp-id"))
    }
}
