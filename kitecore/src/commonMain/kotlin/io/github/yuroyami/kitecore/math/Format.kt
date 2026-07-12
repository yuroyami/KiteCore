/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.math

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Returns this value rounded to [decimals] fractional digits.
 *
 * Rounding mode is half-up with ties rounded away from zero, so `2.5.roundTo(0)`
 * is `3.0` and `(-2.5).roundTo(0)` is `-3.0`. Results that round to zero return
 * positive zero. The result is the nearest representable [Double], so tiny
 * binary representation error can remain.
 *
 * Throws [IllegalArgumentException] when [decimals] is outside `0..15`.
 * NaN and infinities are returned unchanged. Values whose magnitude is at
 * least 2^52, or whose scaled magnitude exceeds [Long.MAX_VALUE], are returned
 * unchanged because they carry no meaningful digits at the requested precision.
 */
public fun Double.roundTo(decimals: Int): Double {
    requireDecimals(decimals)
    if (!isFinite() || abs(this) >= MAX_FRACTIONAL_DOUBLE) return this
    val pow = POW10[decimals]
    val scaled = abs(this) * pow
    if (scaled >= LONG_MAX_AS_DOUBLE) return this
    val units = floor(scaled + 0.5).toLong()
    val result = units.toDouble() / pow
    return if (this < 0.0 && units != 0L) -result else result
}

/**
 * Returns this value rounded to [decimals] fractional digits.
 *
 * Converts to [Double], applies [roundTo] with half-up rounding (ties away
 * from zero), and converts back. Throws [IllegalArgumentException] when
 * [decimals] is outside `0..15`. NaN and infinities are returned unchanged.
 */
public fun Float.roundTo(decimals: Int): Float = toDouble().roundTo(decimals).toFloat()

/**
 * Returns this value rounded down (toward negative infinity) at [decimals] fractional digits.
 *
 * For example `3.75.floorTo(1)` is `3.7` and `(-3.11).floorTo(1)` is `-3.2`.
 * Throws [IllegalArgumentException] when [decimals] is outside `0..15`. NaN,
 * infinities, and magnitudes at or above 2^52 are returned unchanged. The
 * result is the nearest representable [Double] to the exact decimal.
 */
public fun Double.floorTo(decimals: Int): Double {
    requireDecimals(decimals)
    if (!isFinite() || abs(this) >= MAX_FRACTIONAL_DOUBLE) return this
    val pow = POW10[decimals].toDouble()
    return floor(this * pow) / pow
}

/**
 * Returns this value rounded up (toward positive infinity) at [decimals] fractional digits.
 *
 * For example `3.25.ceilTo(1)` is `3.3` and `(-3.75).ceilTo(1)` is `-3.7`.
 * Throws [IllegalArgumentException] when [decimals] is outside `0..15`. NaN,
 * infinities, and magnitudes at or above 2^52 are returned unchanged. The
 * result is the nearest representable [Double] to the exact decimal.
 */
public fun Double.ceilTo(decimals: Int): Double {
    requireDecimals(decimals)
    if (!isFinite() || abs(this) >= MAX_FRACTIONAL_DOUBLE) return this
    val pow = POW10[decimals].toDouble()
    return ceil(this * pow) / pow
}

/**
 * Formats this value with exactly [decimals] fractional digits, locale-independent.
 *
 * Rounding mode is half-up with ties rounded away from zero, and carries
 * propagate across the decimal point: `9.99.formatDecimals(1)` is `"10.0"`.
 * The decimal separator is always `'.'` and no grouping is applied. With
 * [decimals] of zero the result has no decimal point. Values that round to
 * zero are printed without a sign. NaN and infinities format as `"NaN"`,
 * `"Infinity"`, and `"-Infinity"`.
 *
 * Throws [IllegalArgumentException] when [decimals] is outside `0..15`, or
 * when the magnitude scaled by 10^[decimals] exceeds [Long.MAX_VALUE].
 */
public fun Double.formatDecimals(decimals: Int): String {
    requireDecimals(decimals)
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0.0) "Infinity" else "-Infinity"
    val pow = POW10[decimals]
    val scaled = abs(this) * pow
    require(scaled < LONG_MAX_AS_DOUBLE) {
        "Magnitude of $this is too large to format with $decimals decimals."
    }
    val units = floor(scaled + 0.5).toLong()
    val intPart = units / pow
    val fracPart = units % pow
    return buildString {
        if (this@formatDecimals < 0.0 && units != 0L) append('-')
        append(intPart)
        if (decimals > 0) {
            append('.')
            append(fracPart.toString().padStart(decimals, '0'))
        }
    }
}

/**
 * Formats this value with exactly [decimals] fractional digits, locale-independent.
 *
 * Converts to [Double] and delegates to [Double.formatDecimals], so rounding
 * is half-up with ties away from zero and carries propagate:
 * `9.99f.formatDecimals(1)` is `"10.0"`. Throws [IllegalArgumentException]
 * when [decimals] is outside `0..15`.
 */
public fun Float.formatDecimals(decimals: Int): String = toDouble().formatDecimals(decimals)

/**
 * Returns the multiple of [step] closest to this value.
 *
 * Ties round away from zero, so `1.25.roundToNearest(0.5)` is `1.5` and
 * `(-1.25).roundToNearest(0.5)` is `-1.5`. Results that round to zero return
 * positive zero. NaN and infinities are returned unchanged. Throws
 * [IllegalArgumentException] when [step] is not a finite positive number.
 */
public fun Double.roundToNearest(step: Double): Double {
    require(step > 0.0 && step.isFinite()) { "Step must be a finite positive number, was $step." }
    if (!isFinite()) return this
    val steps = floor(abs(this) / step + 0.5)
    val result = steps * step
    return if (this < 0.0 && steps != 0.0) -result else result
}

/**
 * Returns the multiple of [step] closest to this value.
 *
 * Ties round away from zero, so `25.roundToNearest(10)` is `30` and
 * `(-25).roundToNearest(10)` is `-30`. The computation runs in [Long], and
 * [ArithmeticException] is thrown when the rounded result does not fit in
 * [Int]. Throws [IllegalArgumentException] when [step] is not positive.
 */
public fun Int.roundToNearest(step: Int): Int {
    require(step > 0) { "Step must be positive, was $step." }
    val stepL = step.toLong()
    val magnitude = abs(this.toLong())
    val rounded = (magnitude + stepL / 2L) / stepL * stepL
    val signed = if (this < 0) -rounded else rounded
    if (signed < Int.MIN_VALUE.toLong() || signed > Int.MAX_VALUE.toLong()) {
        throw ArithmeticException("Rounding $this to the nearest $step overflows Int.")
    }
    return signed.toInt()
}

/**
 * Formats this value in decimal with [separator] between each group of three digits.
 *
 * Grouping is fixed at three digits and is locale-independent, for example
 * `1234567L.formatGrouped()` is `"1,234,567"`. Negative values keep a single
 * leading minus sign, and [Long.MIN_VALUE] is handled without overflow.
 */
public fun Long.formatGrouped(separator: Char = ','): String {
    val text = toString()
    val digits = if (this < 0L) text.substring(1) else text
    return buildString(text.length + digits.length / 3) {
        if (this@formatGrouped < 0L) append('-')
        for (index in digits.indices) {
            if (index > 0 && (digits.length - index) % 3 == 0) append(separator)
            append(digits[index])
        }
    }
}

/**
 * Formats this value in decimal with [separator] between each group of three digits.
 *
 * Delegates to [Long.formatGrouped]; grouping is fixed at three digits and is
 * locale-independent. [Int.MIN_VALUE] is handled without overflow.
 */
public fun Int.formatGrouped(separator: Char = ','): String = toLong().formatGrouped(separator)

/**
 * Formats this byte count as a human-readable size string such as `"1.5 KiB"`.
 *
 * With [si] false (the default) the base is 1024 and the unit ladder is
 * B, KiB, MiB, GiB, TiB, PiB, EiB. With [si] true the base is 1000 and the
 * ladder is B, kB, MB, GB, TB, PB, EB. The largest unit whose base power does
 * not exceed the magnitude is chosen. Plain byte values are printed without
 * decimals (`"512 B"`); scaled values use [Double.formatDecimals] with
 * [decimals] digits and half-up rounding, so a value close to the next unit
 * may print as the base, for example `"1024.0 KiB"`. Negative counts keep a
 * leading minus sign. Throws [IllegalArgumentException] when [decimals] is
 * outside `0..15`.
 */
public fun Long.formatBytes(decimals: Int = 1, si: Boolean = false): String {
    requireDecimals(decimals)
    val base = if (si) 1000.0 else 1024.0
    val units = if (si) SI_BYTE_UNITS else BINARY_BYTE_UNITS
    var value = abs(this.toDouble())
    var unitIndex = 0
    while (value >= base && unitIndex < units.size - 1) {
        value /= base
        unitIndex++
    }
    val sign = if (this < 0L) "-" else ""
    val body = if (unitIndex == 0) value.toLong().toString() else value.formatDecimals(decimals)
    return "$sign$body ${units[unitIndex]}"
}

/**
 * Formats this fraction as a percentage string, so `0.42.formatPercent()` is `"42%"`.
 *
 * The receiver is multiplied by 100 and formatted with [Double.formatDecimals],
 * so rounding is half-up with ties away from zero, the separator is `'.'`, and
 * [IllegalArgumentException] is thrown when [decimals] is outside `0..15` or
 * the scaled magnitude exceeds [Long.MAX_VALUE]. A trailing `'%'` is appended.
 */
public fun Double.formatPercent(decimals: Int = 0): String =
    (this * 100.0).formatDecimals(decimals) + "%"

/** Powers of ten from 10^0 through 10^15 used for decimal scaling. */
private val POW10: LongArray = longArrayOf(
    1L,
    10L,
    100L,
    1_000L,
    10_000L,
    100_000L,
    1_000_000L,
    10_000_000L,
    100_000_000L,
    1_000_000_000L,
    10_000_000_000L,
    100_000_000_000L,
    1_000_000_000_000L,
    10_000_000_000_000L,
    100_000_000_000_000L,
    1_000_000_000_000_000L,
)

/** Threshold (2^52) above which a Double has no fractional precision left. */
private const val MAX_FRACTIONAL_DOUBLE: Double = 4_503_599_627_370_496.0

/** [Long.MAX_VALUE] as a Double, used to guard scaled conversions. */
private const val LONG_MAX_AS_DOUBLE: Double = 9.223372036854775E18

/** Binary byte unit ladder with base 1024. */
private val BINARY_BYTE_UNITS: List<String> = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

/** SI byte unit ladder with base 1000. */
private val SI_BYTE_UNITS: List<String> = listOf("B", "kB", "MB", "GB", "TB", "PB", "EB")

/** Validates that a decimal digit count is within the supported range. */
private fun requireDecimals(decimals: Int) {
    require(decimals in 0..15) { "Decimals must be in 0..15, was $decimals." }
}
