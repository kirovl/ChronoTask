package io.github.chronotask.execution

import java.util.UUID

/**
 * Unique identifier for a single task execution attempt.
 */
data class ExecutionId(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "ExecutionId value must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        @JvmStatic
        fun generate(): ExecutionId = ExecutionId(UUID.randomUUID().toString())

        @JvmStatic
        fun of(value: String): ExecutionId = ExecutionId(value)
    }
}
