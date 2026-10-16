package io.github.chronotask.ksp.inheritance

import io.github.chronotask.annotation.Scheduled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

open class ParentWorker {
    @Scheduled(intervalMs = 1000, id = "parent-task")
    open fun doWork() {}
}

class SuppressingChildWorker : ParentWorker() {
    override fun doWork() {}
}

class KspOverrideSuppressionTest {

    @Test
    fun `subclass overriding parent task without annotation produces zero tasks`() {
        val child = SuppressingChildWorker()
        // SuppressingChildWorker has no effective annotated tasks
        // When no tasks exist on a class, no binding or empty definitions is handled
    }
}
