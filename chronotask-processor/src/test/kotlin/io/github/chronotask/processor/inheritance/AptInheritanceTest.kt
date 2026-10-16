package io.github.chronotask.processor.inheritance

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AptInheritanceTest {

    @Test
    fun testSubclassInheritsSuperclassTask() {
        TestCompiler.compileAndLoad(
            "com.example.BaseJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class BaseJob {
                    @Scheduled(id = "base-task", intervalMs = 5000L)
                    public void baseWork() {}
                }
            """.trimIndent(),
            "com.example.ChildJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class ChildJob extends BaseJob {
                    @Scheduled(id = "child-task", intervalMs = 2000L)
                    public void childWork() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ChildJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.ChildJob_ChronoTaskBinding", targetInstance)
            assertEquals(2, defs.size)

            val baseDef = defs.first { it.id == "base-task" }
            assertEquals(TaskSchedule.FixedRate(5000L, 0L), baseDef.schedule)

            val childDef = defs.first { it.id == "child-task" }
            assertEquals(TaskSchedule.FixedRate(2000L, 0L), childDef.schedule)
        }
    }

    @Test
    fun testSamePackagePackagePrivateTaskIsInherited() {
        verifyInheritedIds("base", false, setOf("hidden", "visible", "own"))
    }

    @Test
    fun testCrossPackageOnlyPublicTaskIsInherited() {
        verifyInheritedIds("child", false, setOf("visible", "own"))
    }

    @Test
    fun testPackagePrivateTaskIsNotInheritedAcrossIntermediatePackage() {
        verifyInheritedIds("base", true, setOf("visible", "own"))
    }

    private fun verifyInheritedIds(childPackage: String, intermediate: Boolean, expectedIds: Set<String>) {
        val sources = mutableListOf(
            "base.Base" to """
                package base;
                public class Base {
                    @io.github.chronotask.annotation.Scheduled(id = "hidden", intervalMs = 1000)
                    void hidden() {}
                    @io.github.chronotask.annotation.Scheduled(id = "visible", intervalMs = 1000)
                    public void visible() {}
                }
            """.trimIndent()
        )
        if (intermediate) {
            sources += "middle.Middle" to """
                package middle;
                public class Middle extends base.Base {}
            """.trimIndent()
        }
        val parent = if (intermediate) "middle.Middle" else "base.Base"
        sources += "$childPackage.Child" to """
            package $childPackage;
            public class Child extends $parent {
                @io.github.chronotask.annotation.Once(id = "own", delayMs = 1000)
                public void own() {}
            }
        """.trimIndent()
        TestCompiler.compileAndLoad(*sources.toTypedArray()).use { env ->
            val child = env.loadClass("$childPackage.Child").getDeclaredConstructor().newInstance()
            val definitions = env.getDefinitions("${childPackage}.Child_ChronoTaskBinding", child)
            assertEquals(expectedIds, definitions.map { it.id }.toSet())
            val base = env.loadClass("base.Base").getDeclaredConstructor().newInstance()
            assertEquals(setOf("hidden", "visible"),
                env.getDefinitions("base.Base_ChronoTaskBinding", base).map { it.id }.toSet())
        }
    }
}
