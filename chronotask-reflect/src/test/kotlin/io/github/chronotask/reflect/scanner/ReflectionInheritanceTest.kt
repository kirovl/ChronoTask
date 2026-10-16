package io.github.chronotask.reflect.scanner

import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReflectionInheritanceTest {

    open class BaseService {
        @Scheduled(intervalMs = 1000, id = "base-non-overridden")
        open fun baseNonOverridden() {}

        @Scheduled(intervalMs = 2000, id = "base-overridden-no-ann")
        open fun baseOverriddenWithoutAnnotation() {}

        @Scheduled(intervalMs = 3000, id = "base-overridden-with-ann")
        open fun baseOverriddenWithAnnotation() {}
    }

    class SubService : BaseService() {
        // Overrides without annotation -> should suppress base annotation
        override fun baseOverriddenWithoutAnnotation() {}

        // Overrides with annotation -> should register subclass only
        @Scheduled(intervalMs = 5000, id = "sub-override-ann")
        override fun baseOverriddenWithAnnotation() {}

        @TickTask(intervalMs = 10000, id = "sub-own-task")
        fun subOwnTask() {}
    }

    abstract class AbstractBase {
        @Scheduled(intervalMs = 1000)
        abstract fun abstractMethod()
    }

    class ConcreteChild : AbstractBase() {
        override fun abstractMethod() {}
    }

    @Test
    fun `inheritance and override rules correctly applied`() {
        val service = SubService()
        val definitions = ReflectionTaskScanner.scan(service)

        assertEquals(3, definitions.size)
        val byId = definitions.associateBy { it.id }

        // 1. base-non-overridden is present
        assertTrue("base-non-overridden" in byId)

        // 2. base-overridden-no-ann is suppressed
        assertTrue("base-overridden-no-ann" !in byId)

        // 3. base-overridden-with-ann is replaced by sub-override-ann with intervalMs=5000
        assertTrue("base-overridden-with-ann" !in byId)
        val subOverride = byId["sub-override-ann"]
        assertEquals(TaskSchedule.FixedRate(5000L, 0L), subOverride?.schedule)

        // 4. sub-own-task is present
        assertTrue("sub-own-task" in byId)
    }

    @Test
    fun `abstract base method overridden without annotation is suppressed`() {
        val child = ConcreteChild()
        val definitions = ReflectionTaskScanner.scan(child)
        assertTrue(definitions.isEmpty(), "Child overriding abstract method without annotation should have 0 tasks")
    }
}
