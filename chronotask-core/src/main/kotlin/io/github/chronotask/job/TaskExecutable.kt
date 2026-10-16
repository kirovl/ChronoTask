package io.github.chronotask.job

/**
 * Core execution SPI for tasks. Supports Kotlin coroutine suspend functions.
 */
fun interface TaskExecutable {

    /**
     * Executes the task logic within the given [context].
     *
     * @param context The execution context for this invocation.
     */
    suspend fun execute(context: TaskExecutionContext)

    companion object {
        /**
         * Adapts a [JavaTaskExecutable] into a coroutine [TaskExecutable].
         */
        @JvmStatic
        fun fromJava(executable: JavaTaskExecutable): TaskExecutable = TaskExecutable { context ->
            executable.execute(context)
        }

        /**
         * Adapts a standard [Runnable] into a [TaskExecutable].
         */
        @JvmStatic
        fun fromRunnable(runnable: Runnable): TaskExecutable = TaskExecutable {
            runnable.run()
        }
    }
}
