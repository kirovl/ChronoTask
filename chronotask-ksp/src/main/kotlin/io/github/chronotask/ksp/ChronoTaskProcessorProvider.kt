package io.github.chronotask.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class ChronoTaskProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ChronoTaskProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}
