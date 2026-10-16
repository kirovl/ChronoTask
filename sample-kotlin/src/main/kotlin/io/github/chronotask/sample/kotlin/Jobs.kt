package io.github.chronotask.sample.kotlin

import io.github.chronotask.job.ManagedCronJob
import io.github.chronotask.job.ManagedFixedRateJob
import io.github.chronotask.model.TaskExecutionContext
import java.util.concurrent.TimeUnit

class ResourceDelegateService {
    companion object {
        val INSTANCE = ResourceDelegateService()
    }

    fun expireResources() {
        println("[ResourceDelegateService] Expiring stale resources...")
    }
}

class ResourceDelegateExpireJob(
    private val delegateService: ResourceDelegateService
) : ManagedCronJob("*/5 * * * * ?", name = "ResourceDelegateExpireJob") {
    override suspend fun execute(context: TaskExecutionContext) {
        println("[$name] Executing cron job (count=${context.executionCount})")
        delegateService.expireResources()
    }
}

class CollectErrorLogJob : ManagedCronJob("*/10 * * * * ?", name = "CollectErrorLogJob") {
    override suspend fun execute(context: TaskExecutionContext) {
        println("[$name] Collecting error logs...")
    }
}

class TransactionCheckJob : ManagedCronJob("*/15 * * * * ?", name = "TransactionCheckJob") {
    override suspend fun execute(context: TaskExecutionContext) {
        println("[$name] Checking transaction consistency...")
    }
}

/**
 * Explicit registry object holding managed cron jobs.
 */
object CronJobRegistry {
    val jobs: List<ManagedCronJob> by lazy {
        listOf(
            ResourceDelegateExpireJob(
                ResourceDelegateService.INSTANCE
            ),
            CollectErrorLogJob(),
            TransactionCheckJob()
        )
    }
}

/**
 * A deliberately crashing job to prove that failure never impacts sibling tasks or engine!
 */
class CrashingJob : ManagedFixedRateJob(
    interval = 1000,
    unit = TimeUnit.MILLISECONDS,
    name = "DeliberatelyCrashingJob"
) {
    override suspend fun execute(context: TaskExecutionContext) {
        println("[$name] Throwing intentional exception to verify crash isolation (count=${context.executionCount})...")
        throw IllegalStateException("Intentional test crash in $name!")
    }
}
