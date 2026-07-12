/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Formats this duration as hours, minutes, and seconds, such as "1h 23m 45s".
 *
 * Leading zero units are omitted ("1m 5s", "5s"), inner zero units are kept
 * ("1h 0m 5s"), and [Duration.ZERO] formats as "0s". Values are truncated to
 * whole seconds, negative durations get a leading minus sign, and infinite
 * durations format as "Infinity".
 */
public fun Duration.formatHms(): String {
    if (isNegative()) {
        val absolute = absoluteValue.formatHms()
        return if (absolute == "0s") absolute else "-$absolute"
    }
    if (isInfinite()) return toString()
    return toComponents { hours, minutes, seconds, _ ->
        when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

/**
 * Formats this duration as a colon-separated clock string, such as "01:23:45".
 *
 * Durations under an hour format as "MM:SS" ("23:45") and longer durations as
 * "HH:MM:SS", with the hour field growing past two digits when needed
 * ("100:00:00"). Values are truncated to whole seconds, negative durations get a
 * leading minus sign, and infinite durations format as "Infinity".
 */
public fun Duration.formatClock(): String {
    if (isNegative()) {
        val absolute = absoluteValue.formatClock()
        return if (absolute == "00:00") absolute else "-$absolute"
    }
    if (isInfinite()) return toString()
    return toComponents { hours, minutes, seconds, _ ->
        val mm = minutes.toString().padStart(2, '0')
        val ss = seconds.toString().padStart(2, '0')
        if (hours > 0) "${hours.toString().padStart(2, '0')}:$mm:$ss" else "$mm:$ss"
    }
}

/**
 * Formats this duration as its most significant components, such as "1h 23m".
 *
 * Components range from days down to milliseconds. Zero components are skipped,
 * at most [maxParts] components are emitted, and a duration without any nonzero
 * component, [Duration.ZERO] included, formats as "0s". Negative durations get a
 * leading minus sign and infinite durations format as "Infinity".
 *
 * @param maxParts the maximum number of components to include, at least 1.
 * @throws IllegalArgumentException when [maxParts] is less than 1.
 */
public fun Duration.humanReadable(maxParts: Int = 2): String {
    require(maxParts >= 1) { "maxParts must be at least 1, was $maxParts" }
    if (isNegative()) {
        val absolute = absoluteValue.humanReadable(maxParts)
        return if (absolute == "0s") absolute else "-$absolute"
    }
    if (isInfinite()) return toString()
    return toComponents { days, hours, minutes, seconds, nanoseconds ->
        val parts = buildList {
            if (days > 0) add("${days}d")
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (seconds > 0) add("${seconds}s")
            val millis = nanoseconds / 1_000_000
            if (millis > 0) add("${millis}ms")
        }
        if (parts.isEmpty()) "0s" else parts.take(maxParts).joinToString(" ")
    }
}

/**
 * Rounds this duration to the nearest whole second.
 *
 * Rounding happens at millisecond precision with ties rounding away from zero.
 * Infinite durations are returned unchanged.
 */
public fun Duration.roundToSeconds(): Duration {
    if (isInfinite()) return this
    val millis = inWholeMilliseconds
    val half = if (millis >= 0) 500 else -500
    return ((millis + half) / 1000).seconds
}

/**
 * Rounds this duration to the nearest whole minute.
 *
 * Rounding happens at second precision with ties rounding away from zero.
 * Infinite durations are returned unchanged.
 */
public fun Duration.roundToMinutes(): Duration {
    if (isInfinite()) return this
    val seconds = inWholeSeconds
    val half = if (seconds >= 0) 30 else -30
    return ((seconds + half) / 60).minutes
}

/**
 * Rounds this duration to the nearest whole hour.
 *
 * Rounding happens at second precision with ties rounding away from zero.
 * Infinite durations are returned unchanged.
 */
public fun Duration.roundToHours(): Duration {
    if (isInfinite()) return this
    val seconds = inWholeSeconds
    val half = if (seconds >= 0) 1800 else -1800
    return ((seconds + half) / 3600).hours
}

/**
 * Parses a clock string produced by [formatClock] back into a [Duration].
 *
 * Accepts the "MM:SS" and "HH:MM:SS" shapes with an optional leading minus sign,
 * as described on [parseClockOrNull].
 *
 * @throws IllegalArgumentException when [text] is not a valid clock string.
 */
public fun parseClock(text: String): Duration =
    requireNotNull(parseClockOrNull(text)) { "Not a clock-formatted duration: '$text'" }

/**
 * Parses a clock string produced by [formatClock], or returns null when [text] is not valid.
 *
 * Two colon-separated fields are read as minutes and seconds, three as hours,
 * minutes, and seconds. The leading field may have any number of digits, every
 * following field must have exactly two digits and be at most 59, and a single
 * leading minus sign negates the result.
 */
public fun parseClockOrNull(text: String): Duration? {
    val negative = text.startsWith("-")
    val body = if (negative) text.substring(1) else text
    val fields = body.split(':')
    if (fields.size !in 2..3) return null
    val leading = fields[0]
    if (leading.isEmpty() || !leading.all { it in '0'..'9' }) return null
    for (i in 1 until fields.size) {
        val field = fields[i]
        if (field.length != 2 || !field.all { it in '0'..'9' }) return null
    }
    val leadingValue = leading.toLongOrNull() ?: return null
    val magnitude = if (fields.size == 3) {
        val m = fields[1].toInt()
        val s = fields[2].toInt()
        if (m > 59 || s > 59) return null
        leadingValue.hours + m.minutes + s.seconds
    } else {
        val s = fields[1].toInt()
        if (s > 59) return null
        leadingValue.minutes + s.seconds
    }
    return if (negative) -magnitude else magnitude
}

/** Returns the sum of all durations, or [Duration.ZERO] for an empty iterable. */
public fun Iterable<Duration>.sum(): Duration {
    var total = Duration.ZERO
    for (duration in this) total += duration
    return total
}

/**
 * Returns the average of all durations.
 *
 * @throws NoSuchElementException when the iterable is empty.
 */
public fun Iterable<Duration>.average(): Duration {
    var total = Duration.ZERO
    var count = 0
    for (duration in this) {
        total += duration
        count++
    }
    if (count == 0) throw NoSuchElementException("Cannot average an empty iterable.")
    return total / count
}
