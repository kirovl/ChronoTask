package io.github.chronotask.test.release

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

class ReleaseArtifactVerificationTest {

    private val publicationGroup = System.getProperty("chronotask.publicationGroup")
    private val releaseVersion = System.getProperty("chronotask.releaseVersion")
    private val m2Repo = File(System.getProperty("maven.repo.local"), publicationGroup.replace('.', '/'))

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
            val moduleDir = File(m2Repo, "$moduleName/$releaseVersion")
            assertTrue(moduleDir.exists(), "Directory $moduleDir must exist after publishToMavenLocal")

            val jarFile = File(moduleDir, "$moduleName-$releaseVersion.jar")
            val sourcesJar = File(moduleDir, "$moduleName-$releaseVersion-sources.jar")
            val javadocJar = File(moduleDir, "$moduleName-$releaseVersion-javadoc.jar")
            val pomFile = File(moduleDir, "$moduleName-$releaseVersion.pom")
            val moduleFile = File(moduleDir, "$moduleName-$releaseVersion.module")

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

            JarFile(javadocJar).use { jar ->
                assertNotNull(jar.getEntry("index.html"), "Documentation JAR must contain generated API documentation")
            }

            // Validate POM content
            val pomContent = pomFile.readText()
            assertTrue(pomContent.contains("<groupId>$publicationGroup</groupId>"))
            assertTrue(pomContent.contains("<artifactId>$moduleName</artifactId>"))
            assertTrue(pomContent.contains("<version>$releaseVersion</version>"))
            assertTrue(pomContent.contains("<name>$moduleName</name>"))
            assertTrue(pomContent.contains("Apache License, Version 2.0"))
            assertTrue(pomContent.contains("<url>https://github.com/kirovl/ChronoTask</url>"))
            assertFalse(pomContent.contains("SNAPSHOT"), "Release POM must contain zero SNAPSHOT dependencies")
        }
    }

    @Test
    fun testCoreJarDoesNotContainProcessorOrTestClasses() {
        val coreJar = File(m2Repo, "chronotask-core/$releaseVersion/chronotask-core-$releaseVersion.jar")
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
        val processorJar = File(m2Repo, "chronotask-processor/$releaseVersion/chronotask-processor-$releaseVersion.jar")
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
        val kspJar = File(m2Repo, "chronotask-ksp/$releaseVersion/chronotask-ksp-$releaseVersion.jar")
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
        val reflectJar = File(m2Repo, "chronotask-reflect/$releaseVersion/chronotask-reflect-$releaseVersion.jar")
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
