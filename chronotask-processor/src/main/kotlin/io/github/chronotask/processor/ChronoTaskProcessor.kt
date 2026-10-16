package io.github.chronotask.processor

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedAnnotationTypes(
    "io.github.chronotask.annotation.Scheduled",
    "io.github.chronotask.annotation.Cron",
    "io.github.chronotask.annotation.Once",
    "io.github.chronotask.annotation.TickTask"
)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class ChronoTaskProcessor : AbstractProcessor() {

    private lateinit var parser: TaskMetadataParser
    private lateinit var generator: BindingCodeGenerator
    private val processedClassFqcns = mutableSetOf<String>()

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        parser = TaskMetadataParser(
            elementUtils = processingEnv.elementUtils,
            typeUtils = processingEnv.typeUtils,
            messager = processingEnv.messager
        )
        generator = BindingCodeGenerator(processingEnv.filer)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) {
            return false
        }

        val annotatedElements = mutableSetOf<Element>()
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(Scheduled::class.java))
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(Cron::class.java))
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(Once::class.java))
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(TickTask::class.java))

        val targetClasses = mutableSetOf<TypeElement>()

        for (element in annotatedElements) {
            val enclosingClass = findEnclosingClass(element)
            if (enclosingClass == null) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "ChronoTask: Element '${element.simpleName}' cannot be annotated with scheduling annotations outside of a class.",
                    element
                )
                continue
            }
            targetClasses.add(enclosingClass)
        }

        for (targetClass in targetClasses) {
            val fqcn = targetClass.qualifiedName.toString()
            if (fqcn in processedClassFqcns) {
                continue
            }

            val targetMeta = parser.parseTargetClass(targetClass)
            if (targetMeta != null) {
                try {
                    generator.generate(targetMeta)
                    processedClassFqcns.add(fqcn)
                } catch (e: Exception) {
                    processingEnv.messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Failed to generate binding for '$fqcn': ${e.message}",
                        targetClass
                    )
                }
            }
        }

        return false
    }

    private fun findEnclosingClass(element: Element): TypeElement? {
        var curr: Element? = element
        while (curr != null) {
            if (curr is TypeElement && (curr.kind == ElementKind.CLASS || curr.kind == ElementKind.INTERFACE || curr.kind == ElementKind.ENUM || curr.kind == ElementKind.RECORD)) {
                return curr
            }
            curr = curr.enclosingElement
        }
        return null
    }
}
