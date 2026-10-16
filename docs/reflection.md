# Runtime Reflection Guide (`chronotask-reflect`)

`chronotask-reflect` scans annotated methods on target instances at runtime.

## Installation

```kotlin
dependencies {
    implementation("io.github.chronotask:chronotask-core:1.0.0")
    implementation("io.github.chronotask:chronotask-reflect:1.0.0")
}
```

## Usage

```kotlin
import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.reflect.registerAnnotated

class CleanupService {
    @Scheduled(intervalMs = 30_000L)
    fun runCleanup() {
        println("Cleaning up temp files...")
    }
}

val engine = ChronoTask.create()
val service = CleanupService()

// Registers all annotated methods on service instance
val handles = engine.registerAnnotated(service)
engine.start()
```

## ProGuard / R8 Rules

`chronotask-reflect` automatically includes consumer rules to retain annotations and method signatures in minified release builds.

## Non-public target classes

Public suspend task methods declared on a non-public class can be invoked through
runtime reflection. The adapter enables access on both the Java method and the Kotlin
function used for suspend calls. Private annotated methods remain unsupported.

## Annotation string normalization

Reflection, KSP, and APT trim leading and trailing whitespace from annotation IDs,
names, dispatcher keys, Cron expressions, and time zones. Blank IDs use the default
method ID, blank names use the resolved ID, and blank dispatcher keys select the
default dispatcher. Duplicate IDs are checked after normalization before registration.

Default IDs use the declaring class's source qualified name and the source method name
(`package.Outer.Nested#run`), matching KSP/APT. Kotlin `internal` JVM module suffixes
and `@JvmName` aliases are excluded. For classes without a canonical name, reflection
falls back to the JVM class name.

This changes reflection's previous default IDs for nested classes and mangled methods.
Update stored IDs and ID-based lookups, or set an explicit annotation ID to preserve
an existing identifier.

## Default parameters and JVM overloads

Annotated Kotlin methods with a default `TaskExecutionContext` parameter and `@JvmOverloads` register once per source method. Reflection ignores the generated JVM overloads and invokes the original method with the actual execution context, including suspend methods. Inherited methods follow the same override rules: an unannotated override suppresses the inherited task. Real source overloads remain separate tasks and must use distinct IDs.

## Cron validation before registration

Reflection parses Cron expressions using the annotation's Quartz or UNIX dialect during scanning. Invalid syntax raises `TaskConfigurationException` with the task ID and the original `CronParseException` as its cause, before any task in the batch is registered. This prevents earlier tasks in a malformed batch from starting when the engine is already running.
