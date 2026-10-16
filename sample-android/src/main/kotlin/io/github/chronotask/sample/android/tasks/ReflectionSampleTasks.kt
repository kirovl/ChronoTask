package io.github.chronotask.sample.android.tasks

import io.github.chronotask.annotation.Scheduled
import java.util.concurrent.atomic.AtomicInteger

class ReflectionSampleTasks(
    private val onTaskRun: (taskId: String, count: Int, message: String) -> Unit
) {
    private val evictCount = AtomicInteger(0)

    @Scheduled(id = "reflect-cache-evict", intervalMs = 5000L, initialDelayMs = 2000L)
    fun evictCache() {
        val count = evictCount.incrementAndGet()
        onTaskRun("reflect-cache-evict", count, "Reflection-bound cache evict completed (#$count)")
    }
}
