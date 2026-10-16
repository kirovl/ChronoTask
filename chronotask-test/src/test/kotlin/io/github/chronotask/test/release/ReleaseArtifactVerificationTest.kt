package io.github.chronotask.test.release

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

class ReleaseArtifactVerificationTest {

    private val userHome = System.getProperty("user.home")
    private val m2Repo = File(userHome, ".m2/repository/io/github/chronotask")

    @Test
    fun testAllPublishedArtifactsExistWithRequiredComponents() {
        val publishedModules = listOf(
            "chronotask-core",
            "chronotask-reflect",
            "chronotask-ksp",
            "chronotask-processor",
            "chronotask-test"
        )

        for (moduleName in publishedModules) {
            val moduleDir = File(m2Repo, "$moduleName/1.0.0")
            assertTrue(moduleDir.exists(), "Directory $moduleDir must exist after publishToMavenLocal")

            val jarFile = File(moduleDir, "$moduleName-1.0.0.jar")
            val sourcesJar = File(moduleDir, "$moduleName-1.0.0-sources.jar")
            val javadocJar = File(moduleDir, "$moduleName-1.0.0-javadoc.jar")
            val pomFile = File(moduleDir, "$moduleName-1.0.0.pom")
            val moduleFile = File(moduleDir, "$moduleName-1.0.0.module")

            assertTrue(jarFile.exists(), "$jarFile must exist")
            assertTrue(sourcesJar.exists(), "$sourcesJar must exist")
            assertTrue(javadocJar.exists(), "$javadocJar must exist")
            assertTrue(pomFile.exists(), "$pomFile must exist")
            assertTrue(moduleFile.exists(), "$moduleFile must exist")

            // Check non-empty
            assertTrue(jarFile.length() > 0, "$jarFile must not be empty")
            assertTrue(sourcesJar.length() > 0, "$sourcesJar must not be empty")
            assertTrue(javadocJar.length() > 0, "$javadocJar must not be empty")
            assertTrue(pomFile.length() > 0, "$pomFile must not be empty")

            // Validate POM content
            val pomContent = pomFile.readText()
            assertTrue(pomContent.contains("<groupId>io.github.chronotask</groupId>"))
            assertTrue(pomContent.contains("<artifactId>$moduleName</artifactId>"))
            assertTrue(pomContent.contains("<version>1.0.0</version>"))
            assertTrue(pomContent.contains("<name>$moduleName</name>"))
            assertTrue(pomContent.contains("Apache License, Version 2.0"))
            assertFalse(pomContent.contains("SNAPSHOT"), "Release POM must contain zero SNAPSHOT dependencies")
        }
    }

    @Test
    fun testCoreJarDoesNotContainProcessorOrTestClasses() {
        val coreJar = File(m2Repo, "chronotask-core/1.0.0/chronotask-core-1.0.0.jar")
        assertTrue(coreJar.exists())

        JarFile(coreJar).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()

            // Core must contain model, engine, cron, trigger, job, annotation
            assertTrue(entries.any { it.contains("io/github/chronotask/engine/ChronoTaskEngine") })
            assertTrue(entries.any { it.contains("io/github/chronotask/cron/CronExpression") })

            // Core must NOT contain reflection binding, processor, or ksp classes
            assertFalse(entries.any { it.contains("io/github/chronotask/reflect") })
            assertFalse(entries.any { it.contains("io/github/chronotask/processor") })
            assertFalse(entries.any { it.contains("io/github/chronotask/ksp") })
            assertFalse(entries.any { it.contains("FakeChronoClock") })
        }
    }

    @Test
    fun testProcessorJarContainsSpiMetadata() {
        val processorJar = File(m2Repo, "chronotask-processor/1.0.0/chronotask-processor-1.0.0.jar")
        assertTrue(processorJar.exists())

        JarFile(processorJar).use { jar ->
            val spiEntry = jar.getEntry("META-INF/services/javax.annotation.processing.Processor")
            assertNotNull(spiEntry, "Processor JAR must contain javax.annotation.processing.Processor SPI")

            val spiContent = jar.getInputStream(spiEntry).bufferedReader().readText()
            assertTrue(spiContent.contains("io.github.chronotask.processor.ChronoTaskProcessor"))
        }
    }

    @Test
    fun testKspJarContainsSpiMetadata() {
        val kspJar = File(m2Repo, "chronotask-ksp/1.0.0/chronotask-ksp-1.0.0.jar")
        assertTrue(kspJar.exists())

        JarFile(kspJar).use { jar ->
            val spiEntry = jar.getEntry("META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider")
            assertNotNull(spiEntry, "KSP JAR must contain SymbolProcessorProvider SPI")

            val spiContent = jar.getInputStream(spiEntry).bufferedReader().readText()
            assertTrue(spiContent.contains("io.github.chronotask.ksp.ChronoTaskSymbolProcessorProvider"))
        }
    }

    @Test
    fun testReflectJarContainsProGuardAndR8ConsumerRules() {
        val reflectJar = File(m2Repo, "chronotask-reflect/1.0.0/chronotask-reflect-1.0.0.jar")
        assertTrue(reflectJar.exists())

        JarFile(reflectJar).use { jar ->
            val proguardEntry = jar.getEntry("META-INF/proguard/chronotask-reflect.pro")
            assertNotNull(proguardEntry, "Reflect JAR must bundle META-INF/proguard/chronotask-reflect.pro")

            val r8Entry = jar.getEntry("META-INF/com.android.tools/r8/chronotask-reflect.pro")
            assertNotNull(r8Entry, "Reflect JAR must bundle META-INF/com.android.tools/r8/chronotask-reflect.pro")

            val rules = jar.getInputStream(proguardEntry).bufferedReader().readText()
            assertTrue(rules.contains("-keep class io.github.chronotask.annotation.**"))
            assertTrue(rules.contains("@io.github.chronotask.annotation.Scheduled"))
        }
    }
}
