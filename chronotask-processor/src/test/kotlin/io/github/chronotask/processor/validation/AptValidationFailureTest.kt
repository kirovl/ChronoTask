package io.github.chronotask.processor.validation

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.tools.Diagnostic

class AptValidationFailureTest {

    private fun assertCompilationFailsWith(sourceCode: String, expectedMessageSubstring: String, className: String = "com.example.InvalidJob") {
        val source = JavaFileObjects.forSourceString(className, sourceCode)
        val compilation = TestCompiler.compile(source)
        assertEquals(Compilation.Status.FAILURE, compilation.status())
        val errors = compilation.diagnostics().filter { it.kind == Diagnostic.Kind.ERROR }.map { it.getMessage(null) }
        assertTrue(
            errors.any { it.contains(expectedMessageSubstring) },
            "Expected error containing '$expectedMessageSubstring', but got: $errors"
        )
    }

    @Test
    fun testAbstractMethodRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public abstract class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                public abstract void run();
            }
            """.trimIndent(),
            "must not be abstract"
        )
    }

    @Test
    fun testStaticMethodRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                public static void run() {}
            }
            """.trimIndent(),
            "must not be static"
        )
    }

    @Test
    fun testProtectedMethodRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                protected void run() {}
            }
            """.trimIndent(),
            "unsupported visibility (protected)"
        )
    }

    @Test
    fun testPrivateMethodRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                private void run() {}
            }
            """.trimIndent(),
            "unsupported visibility (private)"
        )
    }

    @Test
    fun testNonVoidReturnTypeRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                public String run() { return "hello"; }
            }
            """.trimIndent(),
            "must return void"
        )
    }

    @Test
    fun testInvalidParametersRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                public void run(int a, String b) {}
            }
            """.trimIndent(),
            "invalid parameter count"
        )
    }

    @Test
    fun testMultipleSchedulingAnnotationsRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;
            import io.github.chronotask.annotation.Cron;

            public class InvalidJob {
                @Scheduled(intervalMs = 1000L)
                @Cron(expression = "0 0 12 * * ?")
                public void run() {}
            }
            """.trimIndent(),
            "multiple scheduling annotations"
        )
    }

    @Test
    fun testDuplicateTaskIdRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(id = "same-task-id", intervalMs = 1000L)
                public void taskOne() {}

                @Scheduled(id = "same-task-id", intervalMs = 2000L)
                public void taskTwo() {}
            }
            """.trimIndent(),
            "Duplicate task ID 'same-task-id'"
        )
    }

    @Test
    fun testReservedSuffixRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class MyService_ChronoTaskBinding {
                @Scheduled(intervalMs = 1000L)
                public void run() {}
            }
            """.trimIndent(),
            "ends with reserved suffix '_ChronoTaskBinding'",
            "com.example.MyService_ChronoTaskBinding"
        )
    }

    @Test
    fun testInvalidCronExpressionRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Cron;

            public class InvalidJob {
                @Cron(expression = "invalid cron string")
                public void run() {}
            }
            """.trimIndent(),
            "invalid Cron expression"
        )
    }

    @Test
    fun testInvalidTimeZoneRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Cron;

            public class InvalidJob {
                @Cron(expression = "0 0 12 * * ?", zone = "Invalid/ZoneName_123")
                public void run() {}
            }
            """.trimIndent(),
            "invalid timezone"
        )
    }

    @Test
    fun testInvalidIntervalMsRejected() {
        assertCompilationFailsWith(
            """
            package com.example;
            import io.github.chronotask.annotation.Scheduled;

            public class InvalidJob {
                @Scheduled(intervalMs = 0L)
                public void run() {}
            }
            """.trimIndent(),
            "invalid intervalMs"
        )
    }
}
