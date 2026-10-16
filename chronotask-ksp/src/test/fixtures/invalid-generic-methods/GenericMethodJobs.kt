package invalid.genericmethods

import io.github.chronotask.annotation.Once

class GenericSyncJob {
    @Once
    fun <T : Any> run() {}
}

class GenericSuspendJob {
    @Once
    suspend fun <T : Any> run() {}
}

class ReifiedGenericJob {
    @Once
    inline fun <reified T : Any> run() {}
}
