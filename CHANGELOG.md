# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Add `TaskHandle.unregister()` for atomic registration-specific removal; custom handles can implement the operation without changing existing handle methods.

### Fixed
- Make Android sample tests deterministic with coroutine test dispatchers and correct trigger times; atomically retain events, count failures once, refresh terminal UI state, and isolate statistics across engine recreation.
- Apply JUnit Platform to Android unit test tasks so Jupiter tests are discovered instead of reporting a successful zero-test run.
- Guard Java APT rollback against self-suppression when a handle throws the original registration exception, preserving the original error and completing remaining cleanup.
- Release concurrency reservations after execution launch failures and drain failed queued launches with terminal feedback, allowing dispatcher recovery without stranded tasks.
- Record timer, clock, and submission exceptions from the entire scheduler loop in task snapshots, while preserving resumable cancellation and protecting newer scheduler generations.
- Alias KSP binding imports when generic parameter names shadow framework classes or generic bounds.
- Complete execution feedback from custom runner results and deduplicate start/terminal callbacks, keeping fixed-delay schedules recurring when runners omit callbacks.
- Reject non-progressing custom trigger times before dispatch, preventing repeated executions and scheduler loops outside misfire handling.
- Generate valid KSP binding functions for variant generic classes and self/cross-referencing bounds, and qualify engine handle types when target classes are named TaskHandle.
- Expand Kotlin type aliases in KSP task signatures and inheritance matching, supporting chained Unit/context aliases while preserving context nullability validation.
- Disambiguate KSP and Java APT binding names containing underscores using length-delimited class components; update binding imports for targets whose own or enclosing class names contain underscores. Existing names without underscores remain unchanged.
- Include extension receivers in KSP inheritance signatures so unannotated member extensions cannot suppress inherited scheduled methods.
- Attach execution completion handlers outside the concurrency lock so already-cancelled parent scopes cannot invoke cancellation listeners while holding task state locks.
- Validate reflected Cron expressions during batch scanning and report task-specific configuration errors before any registration or execution can occur.
- Ignore JVM overloads generated from Kotlin default parameters during reflection task scanning, preserving source tasks, suspend context passing, and inheritance suppression.
- Reject concurrency policy changes for an existing controller task ID until cancelAll retires its state, preventing stranded queued work and bypassed concurrency limits.
- Reject Quartz question marks embedded in lists, ranges, or steps, and in non-day fields.
- Preserve each queued execution's submitted task definition for dispatch, replacement, overflow, and cancellation callbacks.
- Validate Kotlin source return types for reflected suspend task methods, rejecting non-Unit returns before registration instead of silently discarding results.
- Invalidate in-progress virtual time advances on reset so they cannot overwrite the new timeline or consume its waiters.
- Diagnose KSP task methods declaring type parameters at the original method, including suspend and reified methods, before generating invalid calls.
- Generate wildcard target types for Java non-static inner task bindings, preserving captured outer generic types and bounds without undeclared type variables.
- Validate Kotlin duration precision by millisecond round-trip comparison, accepting finite whole-millisecond durations beyond the nanosecond range.
- Cancel individual execution jobs outside the concurrency lock so synchronous cancellation callbacks can safely coordinate with other threads.
- Generate valid star-projected target types for inner task classes capturing generic outer types, including nested inner levels and bounds referring to outer type parameters.
- Align reflection default task IDs with source-qualified class and method names, including nested classes, Kotlin internal methods, and JVM-renamed methods. Existing IDs for these reflection tasks change; explicit annotation IDs can preserve old identifiers.
- Validate raw TimeUnit delays before millisecond conversion, rejecting negative sub-millisecond Once delays and recurring initial delays.
- Apply UNIX day-field AND semantics to star-based steps and preserve both day-field restrictions.
- Stop Cron step iteration at the range boundary before integer overflow, including UNIX day-of-week steps.
- Restore JUnit discovery of four negative-duration tests by explicitly returning Unit from coroutine test methods.
- Preserve later virtual clock readings during reentrant or concurrent time advancement.
- Reject negative sub-millisecond Java and Kotlin durations before converting timer waits and virtual time advances to milliseconds.
- Roll back annotation registration batches through their original handles, preserving replacement tasks sharing an ID.
- Normalize reflection annotation strings and detect duplicate IDs after normalization consistently with KSP/APT.
- Generate internal KSP bindings for internal task classes and classes inside internal containers, and diagnose inaccessible class visibility early.
- Enable Kotlin reflection access for public suspend task methods declared on non-public classes.
- Preserve replacement schedulers when cancellation callbacks pause or resume tasks, and ignore superseded pause operations.
- Search Cron timezone transition boundaries so half-hour DST overlaps retain both matching occurrences.
- Preserve sub-millisecond precision in fixed-rate misfire counts, keeping catch-up limits and fire-once recovery exact.
- Round up waits to future instants and recheck the clock after waking to prevent early scheduled execution.
- Reject late execution submissions after task cancellation and isolate concurrent same-ID unregistration from replacement tasks.
- Exclude inaccessible ancestor methods from Java APT bindings while preserving inherited public and same-package methods.
- Keep fixed-delay schedules recurring after skipped overdue triggers.
- Preserve fixed-delay completion feedback for fast executions, manual triggers, and replaced queued triggers.
- Enforce the catch-up limit across the entire missed interval for fixed-rate, cron, and custom schedules.
- Execute tasks on the dispatcher selected by their `dispatcherKey`.
- Preserve pending fixed-delay execution feedback across pause/resume without overlapping automatic executions.
- Discard queued executions before cancelling running tasks and release concurrency slots when cancellation precedes dispatch.
- Isolate execution callbacks and concurrency state across registrations sharing the same task ID.
- Recover cron schedules after long outages without enumerating every missed trigger.
- Reject registrations that race with engine shutdown before committing them to the registry.
- Emit a single terminal notification for Errors escaping task runners, preserving execution state and fixed-delay feedback.

## [1.0.0] - 2026-09-21

### Added
- **Core Scheduling Engine (`chronotask-core`)**:
  - High-performance, zero-allocation-hot-path task engine supporting `FixedRate`, `FixedDelay`, `Cron`, `Once`, and custom triggers.
  - Concurrency policies: `ALLOW`, `SKIP_IF_RUNNING`, `QUEUE_ONE`, and bounded `SERIAL` (`DROP_OLDEST`, `DROP_LATEST`, `REJECT`).
  - Misfire recovery policies: `SKIP`, `FIRE_ONCE`, and bounded `CatchUp(maxExecutions)`.
  - Comprehensive engine lifecycle management (`CREATED`, `RUNNING`, `PAUSED`, `STOPPED`) and per-task lifecycle controls (`pause`, `resume`, `cancel`, `triggerNow`).
  - Cron parser and jump-calculator supporting 6/7-field Quartz and 5-field UNIX expressions with daylight saving time (DST) support.
- **Fluent Public API & Developer Experience**:
  - Kotlin type-safe DSL with extension functions and coroutine support (`engine.schedule { ... }`).
  - Pure Java Builder API (`TaskBuilder`, `JavaTaskExecutable`) with zero Kotlin type leakage.
  - Self-contained job interfaces (`ChronoJob`, `ManagedCronJob`).
- **Annotation & Metadata Binding**:
  - Unified declarative annotation contract: `@Scheduled`, `@Cron`, `@Once`, `@TickTask`.
  - `chronotask-reflect`: Runtime reflection binding with zero-overhead registration and automatic ProGuard/R8 consumer rules.
  - `chronotask-ksp`: Compile-time Kotlin Symbol Processing code generator producing direct-dispatch bindings (`<ClassName>_ChronoTaskBinding.kt`) with zero runtime reflection.
  - `chronotask-processor`: Standard JSR-269 Java Annotation Processor producing type-safe Java bindings (`<ClassName>_ChronoTaskBinding.java`) with checked exception passthrough and zero runtime reflection.
- **Deterministic Testing Support (`chronotask-test`)**:
  - `FakeChronoClock`, `VirtualChronoTimer`, and `VirtualTimeSource` for fast, reproducible, zero-sleep scheduler unit testing.
- **Packaging & Production Hardening**:
  - Full Maven Central publishing configuration with POM metadata, Sources JARs, Javadoc JARs, and in-memory PGP signing.
  - Reproducible archive builds (`preserveFileTimestamps = false`, `reproducibleFileOrder = true`).
  - Binary compatibility validator (`apiCheck`) baseline established for all published modules.
