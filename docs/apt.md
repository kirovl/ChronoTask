# Java Annotation Processing Guide (`chronotask-processor`)

`chronotask-processor` is a standard JSR-269 Java Annotation Processor that generates direct method-call bindings with checked exception support and **zero runtime reflection**.

## Setup

```kotlin
dependencies {
    implementation("io.github.kirovl:chronotask-core:1.0.1")
    annotationProcessor("io.github.kirovl:chronotask-processor:1.0.1")
}
```

## Usage

```java
package com.example.jobs;

import io.github.chronotask.annotation.Scheduled;
import java.io.IOException;

public class CleanupJob {
    @Scheduled(intervalMs = 10000L)
    public void cleanup() throws IOException {
        // Checked exceptions are supported and passed directly to the engine
    }
}
```

In your application bootstrap:

```java
import com.example.jobs.CleanupJob;
import com.example.jobs.CleanupJob_ChronoTaskBinding;
import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import java.util.List;

ChronoTaskEngine engine = ChronoTask.create();
CleanupJob job = new CleanupJob();

// Generated static register method
List<TaskHandle> handles = CleanupJob_ChronoTaskBinding.register(engine, job);
engine.start();
```

## Inherited methods

Bindings include public ancestor task methods and package-private methods actually
inherited by the target class. A package-private ancestor method is excluded when
inheritance crosses a different package, even if a later subclass returns to the
ancestor's package. The ancestor's own binding still includes that method.

## Generic inner classes

Non-static inner task classes can capture generic outer instances. Their generated
`definitions` and `register` methods use wildcard target types such as
`Outer<?>.Inner<?>`, including multi-level inner nesting and bounds referring to an
outer type parameter. Static nesting does not capture unrelated outer type parameters.

## Binding Names

When no class name in the enclosing chain contains an underscore, bindings retain the existing name: join the class names with `_` and append `_ChronoTaskBinding`. For example, `Flat.Name` generates `Flat_Name_ChronoTaskBinding`.

If any class name contains an underscore, the binding starts with `__`. Each class component is encoded as `<length>_<originalName>`, components are joined with `_`, and the same suffix is appended. Length counts UTF-16 code units. For example, `Flat_Name` generates `__9_Flat_Name_ChronoTaskBinding`, while `Flat_.Name` generates `__5_Flat__4_Name_ChronoTaskBinding`. This keeps literal underscores distinct from nesting separators.

When upgrading, update binding imports and references for targets whose own or enclosing class names contain underscores.
