package io.github.chronotask.processor.generation

import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.processor.TestCompiler
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.sql.SQLException
import java.time.Instant

class CheckedExceptionGenerationTest {

    @Test
    fun testCheckedExceptionDirectPassthrough() = runTest {
        TestCompiler.compileAndLoad(
            "com.example.ExceptionJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import java.io.IOException;
                import java.sql.SQLException;

                public class ExceptionJob {
                    @Scheduled(id = "io-task", intervalMs = 1000L)
                    public void throwIOException() throws IOException {
                        throw new IOException("disk error");
                    }

                    @Scheduled(id = "sql-task", intervalMs = 1000L)
                    public void throwSQLException() throws SQLException {
                        throw new SQLException("db connection timeout");
                    }
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ExceptionJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.ExceptionJob_ChronoTaskBinding", targetInstance)
            assertEquals(2, defs.size)

            val trigger = ScheduledTrigger(
                scheduledAt = Instant.now(),
                discoveredAt = Instant.now()
            )
            val dummyContext = TaskExecutionContext(
                taskId = "test-task",
                trigger = trigger,
                attempt = 1
            )

            val ioTaskDef = defs.first { it.id == "io-task" }
            val ioEx = assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking {
                    ioTaskDef.executable.execute(dummyContext)
                }
            }
            assertEquals("disk error", ioEx.message)

            val sqlTaskDef = defs.first { it.id == "sql-task" }
            val sqlEx = assertThrows(SQLException::class.java) {
                kotlinx.coroutines.runBlocking {
                    sqlTaskDef.executable.execute(dummyContext)
                }
            }
            assertEquals("db connection timeout", sqlEx.message)
        }
    }
}
