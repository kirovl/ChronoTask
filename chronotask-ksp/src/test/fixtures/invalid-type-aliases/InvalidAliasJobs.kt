package io.github.chronotask.ksp.fixtures.aliases

import io.github.chronotask.annotation.Once
import io.github.chronotask.job.TaskExecutionContext

typealias NullableContext = TaskExecutionContext?
typealias ChainedNullableContext = NullableContext
typealias NonNullContext = TaskExecutionContext
typealias WrongContext = String
typealias WrongReturn = Int

class InvalidAliasJobs {
    @Once(id = "nullable-underlying")
    fun nullableUnderlying(ctx: NullableContext) {}

    @Once(id = "nullable-chain")
    suspend fun nullableChain(ctx: ChainedNullableContext) {}

    @Once(id = "nullable-use-site")
    fun nullableUseSite(ctx: NonNullContext?) {}

    @Once(id = "wrong-context")
    fun wrongContext(ctx: WrongContext) {}

    @Once(id = "wrong-return")
    fun wrongReturn(): WrongReturn = 42
}
