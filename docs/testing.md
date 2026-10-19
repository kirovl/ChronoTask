# Deterministic Testing with `chronotask-test`

`chronotask-test` provides virtual time primitives to test complex scheduling logic in milliseconds without `Thread.sleep` or flaky real-time waits.

## Setup

```kotlin
testImplementation("io.github.kirovl:chronotask-test:1.0.1")
```

## Example

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class SchedulerUnitTest {

    @Test
    fun testRecurringTaskTimeline() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val config = EngineConfig(
            clock = timeSource.clock,
            timer = timeSource.timer,
            dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
        )
        val engine = ChronoTask.create(config)

        val runCount = AtomicInteger(0)
        engine.schedule("test-task") {
            fixedRate(interval = 5.seconds, initialDelay = 5.seconds)
            execute { runCount.incrementAndGet() }
        }

        engine.start()
        assertEquals(0, runCount.get())

        // Fast-forward virtual time by 5 seconds
        timeSource.advanceBy(5.seconds)
        assertEquals(1, runCount.get())

        // Fast-forward another 10 seconds
        timeSource.advanceBy(10.seconds)
        assertEquals(3, runCount.get())

        engine.stop()
    }
}
```
