package io.github.chronotask.sample.android

import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.execution.*
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskState
import io.github.chronotask.sample.android.model.EventType
import io.github.chronotask.sample.android.model.SampleTaskManager
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SampleTaskManagerFeedbackTest {
    @Test fun `one failed execution produces one failure count and event`() {
        val time = VirtualTimeSource()
        val manager = SampleTaskManager(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            manager.triggerTask("dsl-failure-demo")
            manager.triggerTask("dsl-failure-demo")
            val item = manager.taskList.value.first { it.id == "dsl-failure-demo" }
            assertEquals(2, item.runCount)
            assertEquals(1, item.successCount)
            assertEquals(1, item.failureCount)
            assertEquals(1, manager.events.value.count { it.taskId == item.id && it.type == EventType.FAILED })
        } finally { manager.stop() }
    }

    @Test fun `automatic completion refreshes running task UI states`() = runTest {
        val time = VirtualTimeSource()
        val manager = SampleTaskManager(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler))))
        try {
            manager.start()
            time.advanceBy(2000)
            testScheduler.advanceUntilIdle()
            assertTrue(manager.taskList.value.none { it.state == TaskState.RUNNING })
            assertEquals(1, manager.taskList.value.first { it.id == "ksp-analytics-sync" }.successCount)
        } finally { manager.stop() }
    }

    @Test fun `engine recreation resets execution statistics`() {
        val time = VirtualTimeSource()
        val manager = SampleTaskManager(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            repeat(2) { manager.triggerTask("dsl-failure-demo") }
            manager.initEngine()
            assertTrue(manager.taskList.value.all {
                it.runCount == 0 && it.successCount == 0 && it.failureCount == 0 &&
                    it.skippedCount == 0 && it.lastStartedAt == null && it.lastCompletedAt == null
            })
        } finally { manager.stop() }
    }

    @Test fun `late old execution callbacks cannot change recreated engine statistics`() = runTest {
        val time = VirtualTimeSource()
        val runner = object : TaskRunner {
            override suspend fun run(definition: TaskDefinition, execution: TaskExecution,
                errorHandler: TaskErrorHandler?, listener: ExecutionListener?): ExecutionResult =
                withContext(NonCancellable) { DefaultTaskRunner.run(definition, execution, errorHandler, listener) }
        }
        val manager = SampleTaskManager(EngineConfig(clock = time.clock, timer = time.timer, runner = runner,
            dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler))))
        try {
            manager.triggerTask("ksp-analytics-sync")
            manager.initEngine()
            val eventCount = manager.events.value.size
            testScheduler.advanceUntilIdle()
            val item = manager.taskList.value.first { it.id == "ksp-analytics-sync" }
            assertEquals(0, item.runCount)
            assertEquals(0, item.successCount)
            assertNull(item.lastCompletedAt)
            assertEquals(eventCount, manager.events.value.size)
        } finally { manager.stop() }
    }

    @Test fun `concurrent event writes retain every event within the capacity`() {
        val manager = SampleTaskManager()
        val pool = Executors.newFixedThreadPool(8)
        val gate = CountDownLatch(1)
        try {
            val tasks = (0 until 8).map { worker -> pool.submit {
                gate.await()
                repeat(50) { index -> manager.addEvent("concurrent", EventType.SUCCESS, "$worker:$index") }
            } }
            gate.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            val events = manager.events.value.filter { it.taskId == "concurrent" }
            assertEquals(400, events.size)
            assertEquals(400, events.map { it.message }.toSet().size)
            assertTrue(manager.events.value.size <= 500)
        } finally { pool.shutdownNow(); manager.stop() }
    }
}
