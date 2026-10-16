package io.github.chronotask.job

import io.github.chronotask.model.TaskDefinition

/**
 * Contract implemented by classes that provide a [TaskDefinition] representation of themselves.
 */
interface JobDefinitionProvider {

    /**
     * Converts this job into an immutable [TaskDefinition].
     */
    fun toTaskDefinition(): TaskDefinition
}
