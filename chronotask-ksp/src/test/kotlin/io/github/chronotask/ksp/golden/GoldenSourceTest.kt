package io.github.chronotask.ksp.golden

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GoldenSourceTest {

    @Test
    fun `generated binding contains definitions and transactional register methods`() {
        val bindingFile = File("build/generated/ksp/test/kotlin/io/github/chronotask/ksp/generation/ScheduledTarget_ChronoTaskBinding.kt")
        assertTrue(bindingFile.exists(), "Binding file must exist: ${bindingFile.absolutePath}")

        val content = bindingFile.readText()
        assertTrue(content.contains("public object ScheduledTarget_ChronoTaskBinding"))
        assertTrue(content.contains("public fun definitions(target: ScheduledTarget): List<TaskDefinition>"))
        assertTrue(content.contains("public fun register(engine: ChronoTaskEngine, target: ScheduledTarget): List<TaskHandle>"))
        assertTrue(content.contains("val registeredHandles = ArrayList<TaskHandle>(defs.size)"))
        assertTrue(content.contains("handle.unregister()"))
        assertTrue(content.contains("t.addSuppressed(rollbackEx)"))
    }
}
