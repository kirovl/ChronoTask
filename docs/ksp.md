# Kotlin Symbol Processing Guide (`chronotask-ksp`)

`chronotask-ksp` generates type-safe direct dispatch bindings at compile-time with **zero runtime reflection**.

## Setup

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

dependencies {
    implementation("io.github.kirovl:chronotask-core:1.0.1")
    ksp("io.github.kirovl:chronotask-ksp:1.0.1")
}
```

## Kotlin 1.9.10 consumers

If your application uses Kotlin 1.9.10, select its matching KSP Gradle plugin:

```kotlin
id("com.google.devtools.ksp") version "1.9.10-1.0.13"
```

The ChronoTask dependencies remain `io.github.kirovl:chronotask-core:1.0.1` and
`io.github.kirovl:chronotask-ksp:1.0.1`. A standalone JVM consumer has verified binding
compilation and execution, including default fixed-delay scheduling, suspend methods,
internal generic targets and declaration-site variance. This does not downgrade the
library's transitive Kotlin runtime dependencies. The repository's own build and
Android sample continue to use Kotlin 2.0.21 and KSP 2.0.21-1.0.27.

## Usage

Define your annotated class:

```kotlin
package com.example.jobs

import io.github.chronotask.annotation.Scheduled

class MetricsReporter {
    @Scheduled(intervalMs = 5000L)
    fun report() {
        println("Metrics reported")
    }
}
```

Register using the generated binding:

```kotlin
import com.example.jobs.MetricsReporter
import com.example.jobs.MetricsReporter_ChronoTaskBinding
import io.github.chronotask.ChronoTask

val engine = ChronoTask.create()
val reporter = MetricsReporter()

// Generated static register method
val handles = MetricsReporter_ChronoTaskBinding.register(engine, reporter)

engine.start()
```

## Visibility

Task classes and their enclosing classes may be `public` or `internal`. An `internal`
task class, object, or enclosing class produces an `internal` binding object usable
within the same Kotlin module. Fully public targets retain public bindings.

Private or protected task classes, including targets enclosed by such classes, cannot
be accessed by a generated file. The processor reports a ChronoTask diagnostic on
the task class instead of emitting an inaccessible binding.

## Inner classes

Bindings support `inner` task classes inside generic outer classes, including multiple
inner levels and inner type parameters bounded by an outer type parameter. Generated
target parameters use star projections (for example, `Outer<*>.Inner<*>`) because task
methods accept only `TaskExecutionContext` or no arguments and return `Unit`.
Registration uses the supplied inner instance and its captured outer instance.

## Generic task methods

Task classes may have type parameters, but annotated task methods must not declare
their own type parameters. This includes ordinary, suspend, and inline reified
methods. KSP reports a ChronoTask error on the original method instead of generating
a call with unresolved type arguments.

## Inherited tasks and member extensions

Unannotated member extension functions do not suppress inherited scheduled methods with the same name and ordinary parameters. Inheritance scanning distinguishes extension receivers from regular members. Annotated extension functions remain unsupported and produce a diagnostic.

## Binding Names

When no class name in the enclosing chain contains an underscore, bindings retain the existing name: join the class names with `_` and append `_ChronoTaskBinding`. For example, `Flat.Name` generates `Flat_Name_ChronoTaskBinding`.

If any class name contains an underscore, the binding starts with `__`. Each class component is encoded as `<length>_<originalName>`, components are joined with `_`, and the same suffix is appended. Length counts UTF-16 code units. For example, `Flat_Name` generates `__9_Flat_Name_ChronoTaskBinding`, while `Flat_.Name` generates `__5_Flat__4_Name_ChronoTaskBinding`. This keeps literal underscores distinct from nesting separators.

When upgrading, update binding imports and references for targets whose own or enclosing class names contain underscores.

## Type Aliases in Task Signatures

Task methods can use Kotlin type aliases, including chained aliases, for `Unit` return types and `TaskExecutionContext` parameters. KSP validates the underlying type and preserves nullability from the alias definition and its use site; nullable context parameters and aliases for unsupported types are rejected. Overrides using aliases follow the same annotation suppression rules as overrides using the underlying type.

## Generic Target Classes

Generated binding functions support invariant, covariant, and contravariant target classes, including self-referencing and cross-parameter bounds. Class variance remains on the target class; the binding's function type parameters are invariant. Imports are aliased when type parameter names overlap with framework or bound class names. Non-static inner targets use star projections for captured outer type parameters.
