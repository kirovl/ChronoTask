package io.github.chronotask.sample.kotlin

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.model.TaskExecutionContext
import java.util.concurrent.TimeUnit

class AppScheduledService {

    @Scheduled(fixedDelay = 2000)
    fun sync() {
        println("[AppScheduledService] sync() called with fixedDelay=2000ms")
    }

    @Scheduled(fixedRate = 3000)
    fun refreshRate() {
        println("[AppScheduledService] refreshRate() called with fixedRate=3000ms")
    }

    @Cron("*/4 * * * * ?")
    fun cleanup() {
        println("[AppScheduledService] cleanup() called with @Cron every 4 seconds")
    }

    @Once(delay = 1000)
    fun init() {
        println("[AppScheduledService] init() called with @Once after 1000ms")
    }

    @TickTask(interval = 2, unit = TimeUnit.SECONDS)
    fun refresh() {
        println("[AppScheduledService] refresh() called with @TickTask every 2s")
    }

    @Scheduled(fixedRate = 5000)
    fun taskWithContext(context: TaskExecutionContext) {
        println("[AppScheduledService] taskWithContext() runCount=${context.executionCount}, fireTime=${context.actualFireTime}")
    }
}
