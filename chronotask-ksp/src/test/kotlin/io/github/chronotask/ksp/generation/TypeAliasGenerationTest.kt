package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

typealias AliasTaskContext = TaskExecutionContext
typealias ChainedTaskContext = AliasTaskContext
typealias AliasTaskUnit = Unit
typealias ChainedTaskUnit = AliasTaskUnit

class TypeAliasTarget {
    val calls = mutableListOf<String>()
    var receivedContext: TaskExecutionContext? = null

    @Once(id = "alias-normal")
    fun normal(): AliasTaskUnit { calls += "normal" }

    @Once(id = "alias-suspend")
    suspend fun suspended(): ChainedTaskUnit { calls += "suspend" }

    @Once(id = "alias-context")
    fun context(ctx: AliasTaskContext): ChainedTaskUnit {
        receivedContext = ctx
        calls += "context"
    }

    @Once(id = "alias-suspend-context")
    suspend fun suspendContext(ctx: ChainedTaskContext): AliasTaskUnit {
        receivedContext = ctx
        calls += "suspend-context"
    }
}

open class TypeAliasBase {
    @Once(id = "alias-inherited")
    open fun inherited(ctx: TaskExecutionContext) {}
}

class TypeAliasChild : TypeAliasBase() {
    override fun inherited(ctx: ChainedTaskContext) {}

    @Once(id = "alias-child")
    fun own() {}
}

class TypeAliasGenerationTest {
    private fun execute(id: String, expectedCall: String, expectsContext: Boolean) = runBlocking<Unit> {
        val target = TypeAliasTarget()
        val definition = TypeAliasTarget_ChronoTaskBinding.definitions(target).first { it.id == id }
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val context = TaskExecutionContext(taskId = id, trigger = ScheduledTrigger(now, now))
        definition.executable.execute(context)
        assertEquals(listOf(expectedCall), target.calls)
        if (expectsContext) assertSame(context, target.receivedContext)
    }

    @Test fun `normal task supports Unit alias`() = execute("alias-normal", "normal", false)
    @Test fun `suspend task supports chained Unit alias`() = execute("alias-suspend", "suspend", false)
    @Test fun `normal task receives context through alias`() = execute("alias-context", "context", true)
    @Test fun `suspend task receives context through chained alias`() =
        execute("alias-suspend-context", "suspend-context", true)

    @Test fun `alias typed override suppresses inherited annotation`() {
        assertEquals(listOf("alias-child"),
            TypeAliasChild_ChronoTaskBinding.definitions(TypeAliasChild()).map { it.id })
    }
}
