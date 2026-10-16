package io.github.chronotask.sample.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.chronotask.model.EngineState
import io.github.chronotask.sample.android.ChronoTaskSampleApp
import io.github.chronotask.sample.android.model.SampleTaskEvent
import io.github.chronotask.sample.android.model.SampleTaskManager
import io.github.chronotask.sample.android.model.SampleTaskUiState
import kotlinx.coroutines.flow.StateFlow

class ChronoTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val taskManager: SampleTaskManager =
        (application as? ChronoTaskSampleApp)?.taskManager ?: SampleTaskManager()

    val engineState: StateFlow<EngineState> = taskManager.engineState
    val taskList: StateFlow<List<SampleTaskUiState>> = taskManager.taskList
    val events: StateFlow<List<SampleTaskEvent>> = taskManager.events

    fun startEngine() = taskManager.start()
    fun pauseEngine() = taskManager.pause()
    fun resumeEngine() = taskManager.resume()
    fun stopEngine() = taskManager.stop()
    fun recreateEngine() = taskManager.initEngine()

    fun triggerTask(taskId: String) = taskManager.triggerTask(taskId)
    fun pauseTask(taskId: String) = taskManager.pauseTask(taskId)
    fun resumeTask(taskId: String) = taskManager.resumeTask(taskId)
    fun cancelTask(taskId: String) = taskManager.cancelTask(taskId)
}
