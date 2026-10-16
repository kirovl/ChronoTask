package io.github.chronotask.sample.android

import android.app.Application
import io.github.chronotask.sample.android.model.SampleTaskManager

class ChronoTaskSampleApp : Application() {

    lateinit var taskManager: SampleTaskManager
        private set

    override fun onCreate() {
        super.onCreate()
        taskManager = SampleTaskManager()
        // Auto-start engine on application process initialization
        taskManager.start()
    }
}
