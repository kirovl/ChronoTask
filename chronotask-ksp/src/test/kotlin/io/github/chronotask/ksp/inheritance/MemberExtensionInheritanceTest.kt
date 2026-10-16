package io.github.chronotask.ksp.inheritance

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

open class MemberExtensionBaseJobs {
    var normalCalls = 0
    var contextSeen: TaskExecutionContext? = null
    var suspendContextSeen: TaskExecutionContext? = null

    @Once(id = "extension-base-normal")
    fun normalTask() { normalCalls++ }

    @Once(id = "extension-base-context")
    fun contextTask(context: TaskExecutionContext) { contextSeen = context }

    @Once(id = "extension-base-suspend")
    suspend fun suspendTask(context: TaskExecutionContext) { suspendContextSeen = context }
}

open class MemberExtensionChildJobs : MemberExtensionBaseJobs() {
    private fun String.normalTask() { error("Extension helper must not execute as a task") }
    private fun String.contextTask(context: TaskExecutionContext) { error("Extension helper must not execute as a task") }
    private suspend fun String.suspendTask(context: TaskExecutionContext) { error("Extension helper must not execute as a task") }

    @Once(id = "extension-child-own")
    fun ownTask() {}
}

class MemberExtensionGrandchildJobs : MemberExtensionChildJobs() {
    @Once(id = "extension-grandchild-own")
    fun grandchildTask() {}
}

class MemberExtensionInheritanceTest {
    private val expectedIds = setOf(
        "extension-base-normal", "extension-base-context", "extension-base-suspend", "extension-child-own"
    )
    private val context = TaskExecutionContext(
        "extension-probe", ScheduledTrigger(Instant.EPOCH, Instant.EPOCH, TriggerSource.MANUAL)
    )

    @Test
    fun `member extensions preserve all inherited tasks and reflection parity`() {
        val target = MemberExtensionChildJobs()
        val definitions = MemberExtensionChildJobs_ChronoTaskBinding.definitions(target)
        assertEquals(expectedIds, definitions.map { it.id }.toSet())
        val engine = DefaultChronoTaskEngine()
        try {
            val reflected = engine.registerAnnotated(target).map { it.taskId }.toSet()
            assertEquals(expectedIds, reflected)
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `inherited normal and context tasks execute original bodies`() = runBlocking<Unit> {
        val target = MemberExtensionChildJobs()
        val definitions = MemberExtensionChildJobs_ChronoTaskBinding.definitions(target)
        definitions.single { it.id == "extension-base-normal" }.executable.execute(context)
        definitions.single { it.id == "extension-base-context" }.executable.execute(context)
        assertEquals(1, target.normalCalls)
        assertSame(context, target.contextSeen)
    }

    @Test
    fun `inherited suspend task receives the actual context`() = runBlocking<Unit> {
        val target = MemberExtensionChildJobs()
        val definitions = MemberExtensionChildJobs_ChronoTaskBinding.definitions(target)
        definitions.single { it.id == "extension-base-suspend" }.executable.execute(context)
        assertSame(context, target.suspendContextSeen)
    }

    @Test
    fun `extension helpers in an intermediate class preserve ancestor tasks`() {
        val target = MemberExtensionGrandchildJobs()
        val definitions = MemberExtensionGrandchildJobs_ChronoTaskBinding.definitions(target)
        assertEquals(expectedIds + "extension-grandchild-own", definitions.map { it.id }.toSet())
    }
}
