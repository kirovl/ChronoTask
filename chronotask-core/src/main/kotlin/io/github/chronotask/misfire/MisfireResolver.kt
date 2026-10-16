package io.github.chronotask.misfire

/**
 * Functional strategy interface for resolving missed schedule timeline triggers.
 */
fun interface MisfireResolver {

    /**
     * Resolves missed trigger(s) according to [MisfireContext.misfirePolicy] and advances the schedule timeline.
     *
     * @param context The misfire context.
     * @return [MisfireResult] containing compensatory triggers and next future scheduled instant.
     */
    fun resolve(context: MisfireContext): MisfireResult
}
