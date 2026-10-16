package io.github.chronotask.ksp.inheritance

import io.github.chronotask.annotation.Scheduled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

open class BaseWorker {
    @Scheduled(intervalMs = 1000, id = "base-worker-task")
    open fun execute() {}
}

class ReplacingWorker : BaseWorker() {
    @Scheduled(intervalMs = 2000, id = "replacing-worker-task")
    override fun execute() {}
}

class KspAnnotatedOverrideTest {

    @Test
    fun `subclass overriding parent task with annotation replaces parent task completely`() {
        val child = ReplacingWorker()
        val definitions = ReplacingWorker_ChronoTaskBinding.definitions(child)

        assertEquals(1, definitions.size)
        val def = definitions.single()
        assertEquals("replacing-worker-task", def.id)
    }
}
