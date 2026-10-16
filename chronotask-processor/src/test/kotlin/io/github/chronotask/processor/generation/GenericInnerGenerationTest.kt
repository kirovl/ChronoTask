package io.github.chronotask.processor.generation

import io.github.chronotask.processor.TestCompiler
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import java.time.Instant

class GenericInnerGenerationTest {
    private val source = """
        package com.example;
        import io.github.chronotask.annotation.Once;
        public class GenericOuter<T extends CharSequence> {
            public int calls;
            public class Job {
                @Once(enabled = false) public void run() { calls++; }
            }
            public class BoundedJob<U extends T> {
                @Once(enabled = false) public void run() { calls++; }
            }
            public class Middle<U> {
                public class Job {
                    @Once(enabled = false) public void run() { calls++; }
                }
            }
            public static class StaticContainer<V> {
                public int calls;
                public class Job {
                    @Once(enabled = false) public void run() { calls++; }
                }
            }
        }
    """.trimIndent()

    @Test fun `non-static inner bindings capture generic outer and bounded inner types`() = runBlocking {
        TestCompiler.compileAndLoad("com.example.GenericOuter" to source).use { env ->
            val outerClass = env.loadClass("com.example.GenericOuter")
            val outer = outerClass.getDeclaredConstructor().newInstance()
            val now = Instant.EPOCH
            val context = TaskExecutionContext("probe", ScheduledTrigger(now, now, TriggerSource.MANUAL))
            for (name in listOf("Job", "BoundedJob")) {
                val innerClass = env.loadClass("com.example.GenericOuter\$$name")
                val inner = innerClass.getDeclaredConstructor(outerClass).newInstance(outer)
                val binding = "com.example.GenericOuter_${name}_ChronoTaskBinding"
                val definitions = env.getDefinitions(binding, inner)
                definitions.single().executable.execute(context)
                assertTrue(env.getGeneratedSource(binding).contains("GenericOuter<?>.$name"))
            }
            assertEquals(2, outerClass.getField("calls").getInt(outer))
        }
    }

    @Test fun `multi-level inner and static nested containers generate valid targets`() = runBlocking {
        TestCompiler.compileAndLoad("com.example.GenericOuter" to source).use { env ->
            val outerClass = env.loadClass("com.example.GenericOuter")
            val outer = outerClass.getDeclaredConstructor().newInstance()
            val middleClass = env.loadClass("com.example.GenericOuter\$Middle")
            val middle = middleClass.getDeclaredConstructor(outerClass).newInstance(outer)
            val deepClass = env.loadClass("com.example.GenericOuter\$Middle\$Job")
            val deep = deepClass.getDeclaredConstructor(middleClass).newInstance(middle)
            val staticClass = env.loadClass("com.example.GenericOuter\$StaticContainer")
            val container = staticClass.getDeclaredConstructor().newInstance()
            val staticInnerClass = env.loadClass("com.example.GenericOuter\$StaticContainer\$Job")
            val staticInner = staticInnerClass.getDeclaredConstructor(staticClass).newInstance(container)
            val now = Instant.EPOCH
            val context = TaskExecutionContext("probe", ScheduledTrigger(now, now, TriggerSource.MANUAL))
            env.getDefinitions("com.example.GenericOuter_Middle_Job_ChronoTaskBinding", deep)
                .single().executable.execute(context)
            env.getDefinitions("com.example.GenericOuter_StaticContainer_Job_ChronoTaskBinding", staticInner)
                .single().executable.execute(context)
            assertEquals(1, outerClass.getField("calls").getInt(outer))
            assertEquals(1, staticClass.getField("calls").getInt(container))
        }
    }
}
