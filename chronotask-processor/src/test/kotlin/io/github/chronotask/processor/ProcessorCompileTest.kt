package io.github.chronotask.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.tools.JavaFileObject

class ProcessorCompileTest {

    @Test
    fun testBasicCompilation() {
        val source: JavaFileObject = JavaFileObjects.forSourceString(
            "com.example.TestJob",
            """
            package com.example;

            import io.github.chronotask.annotation.Scheduled;

            public class TestJob {
                @Scheduled(intervalMs = 5000L)
                public void run() {}
            }
            """.trimIndent()
        )

        val compilation: Compilation = Compiler.javac()
            .withProcessors(ChronoTaskProcessor())
            .compile(source)

        assertEquals(Compilation.Status.SUCCESS, compilation.status())
    }
}
