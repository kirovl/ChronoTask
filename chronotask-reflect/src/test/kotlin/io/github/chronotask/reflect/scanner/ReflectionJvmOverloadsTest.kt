package io.github.chronotask.reflect.scanner

import io.github.chronotask.reflect.ReflectionTaskScanner

import io.github.chronotask.annotation.Once
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class ReflectionJvmOverloadsTest {
    class DefaultIdJobs {
        var seen: TaskExecutionContext? = null
        @JvmOverloads
        @Once
        fun run(context: TaskExecutionContext = error("Default context must not be used")) { seen = context }
    }
    class ExplicitIdJobs {
        var seen: TaskExecutionContext? = null
        @JvmOverloads
        @Once(id = "overloaded-task")
        fun run(context: TaskExecutionContext = error("Default context must not be used")) { seen = context }
    }

    class SuspendJobs {
        var seen: TaskExecutionContext? = null
        @JvmOverloads
        @Once(id = "suspend-overloaded-task")
        suspend fun run(context: TaskExecutionContext = error("Default context must not be used")) { seen = context }
    }
    open class BaseJobs {
        var seen: TaskExecutionContext? = null
        @JvmOverloads
        @Once(id = "inherited-overloaded-task")
        open fun run(context: TaskExecutionContext = error("Default context must not be used")) { seen = context }
    }
    class InheritedJobs : BaseJobs()
    class UnannotatedOverrideJobs : BaseJobs() {
        override fun run(context: TaskExecutionContext) { seen = context }
    }
    class AnnotatedOverrideJobs : BaseJobs() {
        @Once(id = "override-task")
        override fun run(context: TaskExecutionContext) { seen = context }
    }
    class RealOverloadJobs {
        @Once(id = "no-context-task") fun run() {}
        @Once(id = "with-context-task") fun run(context: TaskExecutionContext = error("unused")) {}
    }
    class DuplicateOverloadJobs {
        @Once(id = "duplicate-task") fun run() {}
        @Once(id = "duplicate-task") fun run(context: TaskExecutionContext) {}
    }

    class InvalidSignatureJobs {
        @JvmOverloads
        @Once
        fun run(value: String = "unused") {}
    }

    private val context = TaskExecutionContext("probe", ScheduledTrigger(Instant.EPOCH, Instant.EPOCH, TriggerSource.MANUAL))

    @Test fun `default id overloads represent one source task`() = runBlocking<Unit> {
        val jobs = DefaultIdJobs()
        val definitions = ReflectionTaskScanner.scan(jobs)
        assertEquals(1, definitions.size)
        definitions.single().executable.execute(context)
        assertSame(context, jobs.seen)
    }
    @Test fun `explicit id overloads represent one source task`() = runBlocking<Unit> {
        val jobs = ExplicitIdJobs()
        val definitions = ReflectionTaskScanner.scan(jobs)
        assertEquals(1, definitions.size)
        definitions.single().executable.execute(context)
        assertSame(context, jobs.seen)
    }
    @Test fun `suspend overload receives actual context`() = runBlocking<Unit> {
        val jobs = SuspendJobs()
        val definitions = ReflectionTaskScanner.scan(jobs)
        assertEquals(1, definitions.size)
        definitions.single().executable.execute(context)
        assertSame(context, jobs.seen)
    }
    @Test fun `inherited overload represents one source task`() = runBlocking<Unit> {
        val jobs = InheritedJobs()
        val definitions = ReflectionTaskScanner.scan(jobs)
        assertEquals(listOf("inherited-overloaded-task"), definitions.map { it.id })
        definitions.single().executable.execute(context)
        assertSame(context, jobs.seen)
    }
    @Test fun `unannotated override suppresses inherited generated overload`() {
        assertTrue(ReflectionTaskScanner.scan(UnannotatedOverrideJobs()).isEmpty())
    }
    @Test fun `annotated override replaces inherited generated overload`() = runBlocking<Unit> {
        val jobs = AnnotatedOverrideJobs()
        val definitions = ReflectionTaskScanner.scan(jobs)
        assertEquals(listOf("override-task"), definitions.map { it.id })
        definitions.single().executable.execute(context)
        assertSame(context, jobs.seen)
    }
    @Test fun `real source overloads remain separate tasks`() {
        val definitions = ReflectionTaskScanner.scan(RealOverloadJobs())
        assertEquals(setOf("no-context-task", "with-context-task"), definitions.map { it.id }.toSet())
    }
    @Test fun `real source overloads retain duplicate id validation`() {
        assertThrows(TaskConfigurationException::class.java) { ReflectionTaskScanner.scan(DuplicateOverloadJobs()) }
    }
    @Test fun `generated zero argument overload does not hide invalid source parameters`() {
        val error = assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(InvalidSignatureJobs())
        }
        assertTrue(error.message!!.contains("invalid parameters"))
    }
}
