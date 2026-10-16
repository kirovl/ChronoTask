package io.github.chronotask.test.release

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.job.JavaTaskExecutable
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ConsumerMatrixIntegrationTest {

    private val userHome = System.getProperty("user.home")
    private val publicationGroup = System.getProperty("chronotask.publicationGroup")
    private val releaseVersion = System.getProperty("chronotask.releaseVersion")
    private val m2Repo = File(System.getProperty("maven.repo.local"), publicationGroup.replace('.', '/'))

    @Test
    fun testKotlinJvmCoreConsumerFromPublishedJar() {
        val coreJar = File(m2Repo, "chronotask-core/$releaseVersion/chronotask-core-$releaseVersion.jar")
        val coroutinesJar = File(userHome, ".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.9.0").walkTopDown().firstOrNull { it.name.endsWith(".jar") }

        val urls = listOfNotNull(coreJar.toURI().toURL(), coroutinesJar?.toURI()?.toURL()).toTypedArray()
        val classLoader = URLClassLoader(urls, javaClass.classLoader)

        val chronoTaskClass = classLoader.loadClass("io.github.chronotask.ChronoTask")
        assertNotNull(chronoTaskClass)
    }

    @Test
    fun testKotlinJvmReflectionConsumer() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        class SampleConsumerService {
            val count = AtomicInteger(0)

            @Scheduled(intervalMs = 1000L, initialDelayMs = 1000L)
            fun tick() {
                count.incrementAndGet()
            }
        }

        val service = SampleConsumerService()
        val handles = engine.registerAnnotated(service)
        assertEquals(1, handles.size)

        engine.start()
        assertEquals(0, service.count.get())
        timeSource.advanceBy(1000L)
        assertEquals(1, service.count.get())

        engine.stop()
    }

    @Test
    fun testJavaJvmCoreConsumer() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val javaRan = AtomicInteger(0)
        val def = TaskDefinition(
            id = "java-consumer-task",
            schedule = TaskSchedule.FixedRate(intervalMs = 1000L, initialDelayMs = 1000L),
            executable = io.github.chronotask.job.TaskExecutable.fromJava(JavaTaskExecutable { _ ->
                javaRan.incrementAndGet()
            })
        )

        val handle = engine.register(def)
        assertNotNull(handle)

        engine.start()
        assertEquals(0, javaRan.get())
        timeSource.advanceBy(1000L)
        assertEquals(1, javaRan.get())

        engine.stop()
    }
}
