package io.github.chronotask.sample.android.model

import io.github.chronotask.model.TaskState

enum class EventType {
    START,
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED,
    PAUSED,
    RESUMED,
    MANUAL
}

data class SampleTaskEvent(
    val timestamp: String,
    val taskId: String,
    val type: EventType,
    val message: String,
    val thread: String
)

data class SampleTaskUiState(
    val id: String,
    val bindingType: String,
    val scheduleDescription: String,
    val state: TaskState,
    val runCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val skippedCount: Int,
    val lastStartedAt: String?,
    val lastCompletedAt: String?,
    val concurrencyPolicy: String = "SKIP_IF_RUNNING"
)
