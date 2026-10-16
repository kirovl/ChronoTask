package io.github.chronotask.dsl

import io.github.chronotask.exception.TaskConfigurationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import java.time.Duration as JavaDuration

/**
 * Validates and converts a Kotlin [Duration] to positive milliseconds for task intervals/delays.
 *
 * @throws TaskConfigurationException if the duration is non-positive, infinite, sub-millisecond precision, or overflows.
 */
fun Duration.toScheduleIntervalMs(fieldName: String = "interval"): Long {
    if (isInfinite()) {
        throw TaskConfigurationException("$fieldName must not be infinite")
    }
    if (!isPositive()) {
        throw TaskConfigurationException("$fieldName must be strictly positive: $this")
    }
    if (this < 1.milliseconds || this != inWholeMilliseconds.milliseconds) {
        throw TaskConfigurationException("$fieldName must have at least whole millisecond precision, sub-millisecond precision is not supported: $this")
    }
    val ms = inWholeMilliseconds
    if (ms <= 0L) {
        throw TaskConfigurationException("$fieldName overflows Long milliseconds: $this")
    }
    return ms
}

/**
 * Validates and converts a Kotlin [Duration] to non-negative milliseconds for initial delays or once delays.
 *
 * @throws TaskConfigurationException if the duration is negative, infinite, sub-millisecond precision, or overflows.
 */
fun Duration.toScheduleDelayMs(fieldName: String = "delay"): Long {
    if (isInfinite()) {
        throw TaskConfigurationException("$fieldName must not be infinite")
    }
    if (isNegative()) {
        throw TaskConfigurationException("$fieldName must be non-negative: $this")
    }
    if (this == Duration.ZERO) {
        return 0L
    }
    if (this < 1.milliseconds || this != inWholeMilliseconds.milliseconds) {
        throw TaskConfigurationException("$fieldName must have at least whole millisecond precision, sub-millisecond precision is not supported: $this")
    }
    val ms = inWholeMilliseconds
    if (ms < 0L) {
        throw TaskConfigurationException("$fieldName overflows Long milliseconds: $this")
    }
    return ms
}

/**
 * Validates and converts a Java [java.time.Duration] to positive milliseconds.
 *
 * @throws TaskConfigurationException if the duration is non-positive, sub-millisecond precision, or overflows.
 */
fun JavaDuration.toScheduleIntervalMs(fieldName: String = "interval"): Long {
    if (isNegative || isZero) {
        throw TaskConfigurationException("$fieldName must be strictly positive: $this")
    }
    if (nano % 1_000_000 != 0) {
        throw TaskConfigurationException("$fieldName must have at least whole millisecond precision, sub-millisecond precision is not supported: $this")
    }
    return try {
        val ms = toMillis()
        if (ms <= 0L) {
            throw TaskConfigurationException("$fieldName overflows Long milliseconds: $this")
        }
        ms
    } catch (e: ArithmeticException) {
        throw TaskConfigurationException("$fieldName overflows Long milliseconds: $this", e)
    }
}

/**
 * Validates and converts a Java [java.time.Duration] to non-negative milliseconds.
 *
 * @throws TaskConfigurationException if the duration is negative, sub-millisecond precision, or overflows.
 */
fun JavaDuration.toScheduleDelayMs(fieldName: String = "delay"): Long {
    if (isNegative) {
        throw TaskConfigurationException("$fieldName must be non-negative: $this")
    }
    if (isZero) {
        return 0L
    }
    if (nano % 1_000_000 != 0) {
        throw TaskConfigurationException("$fieldName must have at least whole millisecond precision, sub-millisecond precision is not supported: $this")
    }
    return try {
        toMillis()
    } catch (e: ArithmeticException) {
        throw TaskConfigurationException("$fieldName overflows Long milliseconds: $this", e)
    }
}
