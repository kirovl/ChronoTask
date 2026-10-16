package io.github.chronotask.job

/**
 * Java-friendly functional interface for defining task execution bodies without Kotlin Continuation / Unit ABI complexity.
 */
@FunctionalInterface
fun interface JavaTaskExecutable {

    /**
     * Executes the task synchronously.
     *
     * @param context The execution context.
     * @throws Exception if execution fails.
     */
    @Throws(Exception::class)
    fun execute(context: TaskExecutionContext)
}
