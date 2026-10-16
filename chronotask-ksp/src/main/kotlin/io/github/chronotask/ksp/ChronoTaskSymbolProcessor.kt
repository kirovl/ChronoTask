package io.github.chronotask.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * Main KSP processor discovering scheduling annotations and generating direct task bindings.
 */
internal class ChronoTaskSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    @Suppress("unused") private val options: Map<String, String>
) : SymbolProcessor {

    private val parser = TaskMetadataParser(logger)
    private val generator = BindingCodeGenerator(codeGenerator)

    private val processedClassFqcns = mutableSetOf<String>()

    private val annotationNames = listOf(
        "io.github.chronotask.annotation.Scheduled",
        "io.github.chronotask.annotation.Cron",
        "io.github.chronotask.annotation.Once",
        "io.github.chronotask.annotation.TickTask"
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferredSymbols = mutableListOf<KSAnnotated>()

        val annotatedFunctions = annotationNames
            .flatMap { resolver.getSymbolsWithAnnotation(it) }
            .filterIsInstance<KSFunctionDeclaration>()

        val targetClasses = mutableSetOf<KSClassDeclaration>()

        for (function in annotatedFunctions) {
            if (!function.validate()) {
                deferredSymbols.add(function)
                continue
            }

            val parentClass = function.parentDeclaration as? KSClassDeclaration
            if (parentClass == null) {
                logger.error(
                    "ChronoTask: Top-level function '${function.simpleName.asString()}' cannot be annotated with scheduling annotations.",
                    function
                )
                continue
            }

            targetClasses.add(parentClass)
        }

        for (targetClass in targetClasses) {
            val fqcn = targetClass.qualifiedName?.asString() ?: continue
            if (fqcn in processedClassFqcns) {
                continue
            }

            val targetMeta = parser.parseTargetClass(targetClass)
            if (targetMeta != null) {
                try {
                    generator.generate(targetMeta)
                    processedClassFqcns.add(fqcn)
                } catch (e: Exception) {
                    logger.error("ChronoTask: Failed to generate binding for '$fqcn': ${e.message}", targetClass)
                }
            }
        }

        return deferredSymbols
    }

    private fun KSAnnotated.validate(): Boolean {
        return (this as? KSFunctionDeclaration)?.returnType?.resolve()?.isError == false
    }
}
