package io.github.chronotask.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.model.TaskDefinition
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

object TestCompiler {

    fun compile(vararg sources: JavaFileObject): Compilation {
        return Compiler.javac()
            .withProcessors(ChronoTaskProcessor())
            .compile(*sources)
    }

    fun compileAndLoad(
        vararg sourceCodes: Pair<String, String> // Pair(Fqcn, Code)
    ): CompiledEnvironment {
        val javaFiles = sourceCodes.map { (fqcn, code) ->
            JavaFileObjects.forSourceString(fqcn, code)
        }.toTypedArray()

        val compilation = compile(*javaFiles)
        if (compilation.status() != Compilation.Status.SUCCESS) {
            val errors = compilation.diagnostics().filter { it.kind == Diagnostic.Kind.ERROR }.map { it.getMessage(null) }
            throw IllegalStateException("Annotation processing compilation failed with errors: $errors")
        }

        val generatedSourceFiles = compilation.generatedSourceFiles()

        // Compile all sources + generated sources into bytecode in a temporary directory
        val tempDir = Files.createTempDirectory("chronotask-apt-test")
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(tempDir.toFile()))

        // Gather classpath and disable annotation processors during final bytecode compilation
        val classpath = System.getProperty("java.class.path")
        val options = listOf("-classpath", classpath, "-proc:none")

        val allFiles = mutableListOf<JavaFileObject>()
        allFiles.addAll(javaFiles)
        allFiles.addAll(generatedSourceFiles)

        val task = compiler.getTask(null, fileManager, diagnostics, options, null, allFiles)
        val success = task.call()
        if (!success) {
            val errors = diagnostics.diagnostics.map { "${it.kind}: ${it.getMessage(null)} (line ${it.lineNumber})" }
            throw IllegalStateException("Bytecode compilation of generated files failed with errors:\n" + errors.joinToString("\n"))
        }

        val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), this::class.java.classLoader)

        return CompiledEnvironment(
            compilation = compilation,
            tempDir = tempDir,
            classLoader = classLoader
        )
    }
}

class CompiledEnvironment(
    val compilation: Compilation,
    private val tempDir: Path,
    val classLoader: ClassLoader
) : AutoCloseable {

    fun loadClass(fqcn: String): Class<*> = classLoader.loadClass(fqcn)

    @Suppress("UNCHECKED_CAST")
    fun getDefinitions(bindingClassName: String, targetInstance: Any): List<TaskDefinition> {
        val bindingClass = loadClass(bindingClassName)
        val method = bindingClass.getMethod("definitions", targetInstance.javaClass)
        return method.invoke(null, targetInstance) as List<TaskDefinition>
    }

    @Suppress("UNCHECKED_CAST")
    fun register(bindingClassName: String, engine: ChronoTaskEngine, targetInstance: Any): List<TaskHandle> {
        val bindingClass = loadClass(bindingClassName)
        val method = bindingClass.getMethod("register", ChronoTaskEngine::class.java, targetInstance.javaClass)
        return method.invoke(null, engine, targetInstance) as List<TaskHandle>
    }

    fun getGeneratedSource(bindingFqcn: String): String {
        val path = bindingFqcn.replace('.', '/') + ".java"
        val generated = compilation.generatedSourceFiles().firstOrNull { it.name.endsWith(path) }
            ?: throw IllegalArgumentException("No generated source found for $bindingFqcn")
        return generated.getCharContent(true).toString()
    }

    override fun close() {
        try {
            (classLoader as? URLClassLoader)?.close()
        } catch (_: Exception) {}
        try {
            tempDir.toFile().deleteRecursively()
        } catch (_: Exception) {}
    }
}
