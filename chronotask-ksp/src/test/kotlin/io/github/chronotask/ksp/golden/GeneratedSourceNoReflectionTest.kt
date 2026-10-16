package io.github.chronotask.ksp.golden

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GeneratedSourceNoReflectionTest {

    @Test
    fun `generated sources contain absolutely zero reflection references and zero dynamic paths`() {
        val generatedDir = File("build/generated/ksp/test/kotlin")
        assertTrue(generatedDir.exists(), "Generated KSP directory must exist: ${generatedDir.absolutePath}")

        val generatedFiles = generatedDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(generatedFiles.isNotEmpty(), "At least one generated binding file must exist")

        val forbiddenKeywords = listOf(
            "java.lang.reflect",
            "kotlin.reflect",
            "Method.invoke",
            "KFunction",
            "callSuspend",
            "InvocationTargetException",
            "chronotask-reflect"
        )

        for (file in generatedFiles) {
            val content = file.readText()
            for (keyword in forbiddenKeywords) {
                assertFalse(
                    content.contains(keyword),
                    "Generated file ${file.name} must NOT contain '$keyword'"
                )
            }
        }
    }
}
