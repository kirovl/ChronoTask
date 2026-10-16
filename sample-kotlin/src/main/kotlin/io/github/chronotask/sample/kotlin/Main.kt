package io.github.chronotask.sample.kotlin

import io.github.chronotask.dsl.ChronoTask
import io.github.chronotask.engine.ChronoTaskEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

fun main() = runBlocking {
    println("==================================================")
    println("      ChronoTask Kotlin Demo Starting...          ")
    println("==================================================")

    // 1. Configure Engine with custom error handler to demonstrate crash isolation
    val engine = ChronoTaskEngine.builder()
        .errorHandler { task, context, throwable ->
            println(">>> [Captured Handled Error] Task '${task.name}' (id=${task.id}) threw: ${throwable.message}")
        }
        .build()

    // 2. Register explicit CronJobRegistry list
    println("\n[1] Registering explicit jobs from CronJobRegistry...")
    engine.registerAll(CronJobRegistry.jobs)

    // 3. Register deliberately crashing job to prove crash isolation
    println("\n[2] Registering CrashingJob (fails every 1s, will not affect other jobs)...")
    engine.register(CrashingJob())

    // 4. Register annotated service via KSP generated extension
    println("\n[3] Registering @TickTask, @Cron, @Once annotated service...")
    val service = AppScheduledService()
    engine.registerAnnotatedGenerated(service)

    // 5. Using Kotlin DSL
    println("\n[4] Creating dynamic task with Kotlin DSL...")
    ChronoTask.tick(3, TimeUnit.SECONDS, name = "DynamicDslTickTask") { ctx ->
        println("[DSL TickTask] Fired count=${ctx.executionCount}")
    }

    println("\n>>> Engine is running. Letting tasks run for 6 seconds to observe scheduling & crash isolation...\n")
    delay(6000)

    println("\n==================================================")
    println("      Task Metrics Summary                        ")
    println("==================================================")
    engine.getAllMetrics().forEach { m ->
        println("Task [${m.taskName}]: total=${m.totalRuns}, success=${m.successRuns}, failed=${m.failedRuns}, state=${m.taskState}, lastExecution=${m.lastExecutionStatus}")
    }

    println("\nShutting down engine...")
    engine.shutdown()
    ChronoTask.defaultEngine.shutdown()
    println("ChronoTask Kotlin Demo finished successfully!")
}
