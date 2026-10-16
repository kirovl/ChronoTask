package io.github.chronotask.processor.generation

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId

class CronGenerationTest {

    @Test
    fun testQuartzCronGeneration() {
        TestCompiler.compileAndLoad(
            "com.example.QuartzCronJob" to """
                package com.example;

                import io.github.chronotask.annotation.Cron;
                import io.github.chronotask.cron.CronDialect;

                public class QuartzCronJob {
                    @Cron(expression = "0 0 12 * * ?", zone = "Asia/Shanghai", dialect = CronDialect.QUARTZ)
                    public void dailyNoon() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.QuartzCronJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.QuartzCronJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals(
                TaskSchedule.Cron("0 0 12 * * ?", ZoneId.of("Asia/Shanghai"), CronDialect.QUARTZ),
                def.schedule
            )
        }
    }

    @Test
    fun testUnixCronGeneration() {
        TestCompiler.compileAndLoad(
            "com.example.UnixCronJob" to """
                package com.example;

                import io.github.chronotask.annotation.Cron;
                import io.github.chronotask.cron.CronDialect;

                public class UnixCronJob {
                    @Cron(expression = "*/15 * * * *", dialect = CronDialect.UNIX)
                    public void every15Minutes() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.UnixCronJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.UnixCronJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals(
                TaskSchedule.Cron("*/15 * * * *", ZoneId.of("UTC"), CronDialect.UNIX),
                def.schedule
            )
        }
    }
}
