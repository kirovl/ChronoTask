package io.github.chronotask.ksp.inheritance

import io.github.chronotask.annotation.Scheduled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

open class BaseService {
    @Scheduled(intervalMs = 1000, id = "base-inherited-task")
    open fun inheritedTask() {}

    @Scheduled(intervalMs = 2000, id = "base-to-suppress-task")
    open fun toSuppress() {}

    @Scheduled(intervalMs = 3000, id = "base-to-override-task")
    open fun toOverride() {}
}

class ChildService : BaseService() {
    // 1. inheritedTask is not overridden -> inherited from BaseService

    // 2. toSuppress is overridden WITHOUT annotation -> suppressed
    override fun toSuppress() {}

    // 3. toOverride is overridden WITH annotation -> replaced
    @Scheduled(intervalMs = 4000, id = "child-replaced-task")
    override fun toOverride() {}

    // 4. child unique task
    @Scheduled(intervalMs = 5000, id = "child-unique-task")
    fun childTask() {}
}

class KspInheritanceTest {

    @Test
    fun `inheritance semantics match M8 contract exactly`() {
        val child = ChildService()
        val definitions = ChildService_ChronoTaskBinding.definitions(child)

        assertEquals(3, definitions.size)
        val ids = definitions.map { it.id }.toSet()

        assertTrue(ids.contains("base-inherited-task"), "Base inherited task must be present")
        assertTrue(!ids.contains("base-to-suppress-task"), "Base task overridden without annotation must be suppressed")
        assertTrue(!ids.contains("base-to-override-task"), "Base task overridden with annotation must be replaced")
        assertTrue(ids.contains("child-replaced-task"), "Child annotated override must be present")
        assertTrue(ids.contains("child-unique-task"), "Child unique task must be present")
    }
}
