package io.github.chronotask.processor.generation

import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NamingCollisionTest {
    @Test
    fun `nested separators and literal underscores produce distinct bindings`() {
        TestCompiler.compileAndLoad(
            "com.example.Flat" to """
                package com.example;
                import io.github.chronotask.annotation.Once;
                public class Flat {
                    public static class Name {
                        @Once(id = "flat-nested") public void run() {}
                    }
                    public static class _Name {
                        @Once(id = "underscore-inner") public void run() {}
                    }
                }
            """.trimIndent(),
            "com.example.Flat_Name" to """
                package com.example;
                import io.github.chronotask.annotation.Once;
                public class Flat_Name {
                    @Once(id = "flat-top-level") public void run() {}
                }
            """.trimIndent(),
            "com.example.Flat_" to """
                package com.example;
                import io.github.chronotask.annotation.Once;
                public class Flat_ {
                    public static class Name {
                        @Once(id = "underscore-outer") public void run() {}
                    }
                }
            """.trimIndent(),
            "com.example.__9_Flat_Name" to """
                package com.example;
                import io.github.chronotask.annotation.Once;
                public class __9_Flat_Name {
                    @Once(id = "encoded-looking-source") public void run() {}
                }
            """.trimIndent()
        ).use { env ->
            val cases = listOf(
                Triple("Flat\$Name", "Flat_Name_ChronoTaskBinding", "flat-nested"),
                Triple("Flat_Name", "__9_Flat_Name_ChronoTaskBinding", "flat-top-level"),
                Triple("Flat_\$Name", "__5_Flat__4_Name_ChronoTaskBinding", "underscore-outer"),
                Triple("Flat\$_Name", "__4_Flat_5__Name_ChronoTaskBinding", "underscore-inner"),
                Triple("__9_Flat_Name", "__13___9_Flat_Name_ChronoTaskBinding", "encoded-looking-source")
            )
            for ((targetName, bindingName, taskId) in cases) {
                val target = env.loadClass("com.example.$targetName").getDeclaredConstructor().newInstance()
                assertEquals(taskId, env.getDefinitions("com.example.$bindingName", target).single().id)
            }
        }
    }
}
