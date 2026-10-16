# Java API Guide

ChronoTask provides a fluent Java 17+ Builder API with zero Kotlin runtime leaks and full checked-exception support.

## TaskBuilder Usage

```java
import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.ConcurrencyPolicy;
import io.github.chronotask.model.MisfirePolicy;
import io.github.chronotask.model.TaskDefinition;

import java.time.Duration;

public class JavaExample {
    public void setup() {
        ChronoTaskEngine engine = ChronoTask.create();

        TaskDefinition definition = ChronoTask.newTask("order-sync")
                .fixedRate(Duration.ofSeconds(15), Duration.ofSeconds(5))
                .skipIfRunning()
                .misfireFireOnce()
                .executeRunnable(() -> {
                    // Checked exceptions can be thrown directly if using execute() with JavaTaskExecutable
                    syncOrders();
                })
                .build();

        TaskHandle handle = engine.register(definition);
        engine.start();
    }

    private void syncOrders() {
        // sync logic
    }
}
```

## Checked Exception Passthrough

```java
TaskDefinition def = ChronoTask.newTask("db-backup")
        .fixedDelay(Duration.ofHours(1))
        .execute(context -> {
            // Can throw java.io.IOException or java.sql.SQLException directly!
            performDatabaseBackup();
        })
        .build();
```
