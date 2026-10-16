package io.github.chronotask.model

/**
 * Policy defining how missed triggers (e.g. due to system pause, sleep, or thread starvation) are handled.
 */
sealed interface MisfirePolicy {

    /**
     * Ignores any missed triggers and schedules next execution based on current time.
     */
    data object Skip : MisfirePolicy

    /**
     * Immediately fires a single compensatory execution for missed triggers, then resumes normal timeline.
     */
    data object FireOnce : MisfirePolicy

    /**
     * Sequentially catches up missed triggers up to [maxExecutions].
     *
     * @property maxExecutions The maximum number of catch-up executions allowed. Must be > 0.
     */
    data class CatchUp @JvmOverloads constructor(
        val maxExecutions: Int = 100
    ) : MisfirePolicy {
        init {
            require(maxExecutions > 0) { "maxExecutions must be positive: $maxExecutions" }
        }
    }

    companion object {
        @JvmField
        val SKIP: MisfirePolicy = Skip

        @JvmField
        val FIRE_ONCE: MisfirePolicy = FireOnce

        @JvmField
        val CATCH_UP: MisfirePolicy = CatchUp()

        @JvmStatic
        @JvmOverloads
        fun catchUp(maxExecutions: Int = 100): CatchUp = CatchUp(maxExecutions)
    }
}
