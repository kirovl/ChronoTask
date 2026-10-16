package io.github.chronotask.sample.android

import io.github.chronotask.model.TaskState
import io.github.chronotask.sample.android.model.SampleTaskManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidTaskInteractionTest {

    @Test
    fun `test individual task controls manual trigger pause resume cancel`() = runTest {
        val time = VirtualTimeSource()
        val manager = SampleTaskManager(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(UnconfinedTestDispatcher(testScheduler))))
        manager.start()

        val taskId = "ksp-analytics-sync"

        // Manual trigger
        manager.triggerTask(taskId)
        testScheduler.advanceUntilIdle()
        val triggeredItem = manager.taskList.value.first { it.id == taskId }
        assertEquals(TaskState.SCHEDULED, triggeredItem.state)

        // Pause task
        manager.pauseTask(taskId)
        val pausedItem = manager.taskList.value.first { it.id == taskId }
        assertEquals(TaskState.PAUSED, pausedItem.state)

        // Resume task
        manager.resumeTask(taskId)
        val resumedItem = manager.taskList.value.first { it.id == taskId }
        assertEquals(TaskState.SCHEDULED, resumedItem.state)

        // Cancel task
        manager.cancelTask(taskId)
        val cancelledItem = manager.taskList.value.first { it.id == taskId }
        assertEquals(TaskState.CANCELLED, cancelledItem.state)

        manager.stop()
    }
}
