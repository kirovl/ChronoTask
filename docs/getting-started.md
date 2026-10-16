# Getting Started with ChronoTask

ChronoTask is a modern, lightweight in-memory task scheduling framework for Kotlin and Java on the JVM and Android.

## Installation

Add ChronoTask to your build tool:

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("io.github.chronotask:chronotask-core:1.0.0")
    testImplementation("io.github.chronotask:chronotask-test:1.0.0")
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.chronotask</groupId>
    <artifactId>chronotask-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 5-Minute Quickstart

### Kotlin Quickstart

```kotlin
import io.github.chronotask.ChronoTask
import kotlin.time.Duration.Companion.seconds

fun main() {
    // 1. Create engine
    val engine = ChronoTask.create()

    // 2. Schedule a recurring task using Kotlin DSL
    val handle = engine.schedule("my-cleanup-task") {
        fixedRate(5.seconds)
        execute {
            println("Executing cleanup task at ${System.currentTimeMillis()}")
        }
    }

    // 3. Start the engine
    engine.start()

    // 4. Stop when application shuts down
    // engine.stop()
}
```

### Java Quickstart

```java
import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.TaskDefinition;

import java.time.Duration;

public class Main {
    public static void main(String[] args) {
        // 1. Create engine
        ChronoTaskEngine engine = ChronoTask.create();

        // 2. Build task using TaskBuilder
        TaskDefinition definition = ChronoTask.newTask("my-java-task")
                .fixedRate(Duration.ofSeconds(5))
                .executeRunnable(() -> System.out.println("Java task executed"))
                .build();

        // 3. Register and start
        TaskHandle handle = engine.register(definition);
        engine.start();

        // 4. Clean shutdown
        // engine.stop();
    }
}
```
