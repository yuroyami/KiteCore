/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.math

import kotlin.math.PI
import kotlin.math.abs

/**
 * Linearly interpolates between [start] and [stop] by [fraction].
 *
 * The [fraction] is not clamped: values outside `0.0..1.0` extrapolate beyond
 * the endpoints. A fraction of `0.0` returns [start] and `1.0` returns [stop].
 */
public fun lerp(start: Double, stop: Double, fraction: Double): Double =
    start + (stop - start) * fraction

/**
 * Linearly interpolates between [start] and [stop] by [fraction].
 *
 * The [fraction] is not clamped: values outside `0f..1f` extrapolate beyond
 * the endpoints. A fraction of `0f` returns [start] and `1f` returns [stop].
 */
public fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Returns the fraction at which [value] lies between [start] and [stop].
 *
 * This is the inverse of [lerp]: `inverseLerp(a, b, lerp(a, b, t))` is `t`
 * up to floating point representation. The result is not clamped. Returns
 * `0.0` when [start] equals [stop] to avoid dividing by zero.
 */
public fun inverseLerp(start: Double, stop: Double, value: Double): Double {
    if (start == stop) return 0.0
    return (value - start) / (stop - start)
}

/**
 * Remaps this value from the range `fromStart..fromEnd` to the range `toStart..toEnd`.
 *
 * The receiver is not clamped: values outside the source range map outside the
 * target range. When [fromStart] equals [fromEnd] the result is [toStart].
 */
public fun Double.remap(
    fromStart: Double,
    fromEnd: Double,
    toStart: Double,
    toEnd: Double,
): Double = lerp(toStart, toEnd, inverseLerp(fromStart, fromEnd, this))

/** Returns `true` when this value is evenly divisible by two. */
public fun Int.isEven(): Boolean = this % 2 == 0

/** Returns `true` when this value is not evenly divisible by two. */
public fun Int.isOdd(): Boolean = this % 2 != 0

/** Returns `true` when this value is evenly divisible by two. */
public fun Long.isEven(): Boolean = this % 2L == 0L

/** Returns `true` when this value is not evenly divisible by two. */
public fun Long.isOdd(): Boolean = this % 2L != 0L

/**
 * Returns the decimal digits of the absolute value of this number, most significant first.
 *
 * The sign is discarded, so `(-305).digits()` returns `[3, 0, 5]`. Zero returns
 * `[0]`. [Int.MIN_VALUE] is handled without overflow.
 */
public fun Int.digits(): List<Int> {
    var remaining = abs(this.toLong())
    if (remaining == 0L) return listOf(0)
    val out = ArrayList<Int>(10)
    while (remaining > 0L) {
        out.add((remaining % 10L).toInt())
        remaining /= 10L
    }
    out.reverse()
    return out
}

/**
 * Returns the number of decimal digits in the absolute value of this number.
 *
 * Zero counts as one digit. The sign is not counted, and [Int.MIN_VALUE] is
 * handled without overflow.
 */
public fun Int.digitCount(): Int {
    val text = toString()
    return if (this < 0) text.length - 1 else text.length
}

/**
 * Returns the number of decimal digits in the absolute value of this number.
 *
 * Zero counts as one digit. The sign is not counted, and [Long.MIN_VALUE] is
 * handled without overflow.
 */
public fun Long.digitCount(): Int {
    val text = toString()
    return if (this < 0L) text.length - 1 else text.length
}

/**
 * Returns the greatest common divisor of [a] and [b].
 *
 * Signs are ignored and the result is never negative. `gcd(0, 0)` is `0`.
 * Throws [ArithmeticException] when the result does not fit in [Int], which
 * happens only when it equals 2^31 (for example `gcd(Int.MIN_VALUE, 0)`).
 */
public fun gcd(a: Int, b: Int): Int {
    val result = gcdOfMagnitudes(magnitudeOf(a.toLong()), magnitudeOf(b.toLong()))
    if (result > Int.MAX_VALUE.toULong()) {
        throw ArithmeticException("gcd($a, $b) is $result, which does not fit in Int.")
    }
    return result.toInt()
}

/**
 * Returns the greatest common divisor of [a] and [b].
 *
 * Signs are ignored and the result is never negative. `gcd(0L, 0L)` is `0L`.
 * Throws [ArithmeticException] when the result does not fit in [Long], which
 * happens only when it equals 2^63 (for example `gcd(Long.MIN_VALUE, 0L)`).
 */
public fun gcd(a: Long, b: Long): Long {
    val result = gcdOfMagnitudes(magnitudeOf(a), magnitudeOf(b))
    if (result > Long.MAX_VALUE.toULong()) {
        throw ArithmeticException("gcd($a, $b) is $result, which does not fit in Long.")
    }
    return result.toLong()
}

/**
 * Returns the least common multiple of [a] and [b] as a [Long].
 *
 * Signs are ignored and the result is never negative. Returns `0L` when either
 * argument is zero. The result always fits because the magnitude of an [Int]
 * product never exceeds 2^62, so this overload never throws.
 */
public fun lcm(a: Int, b: Int): Long {
    val la = abs(a.toLong())
    val lb = abs(b.toLong())
    if (la == 0L || lb == 0L) return 0L
    val g = gcdOfMagnitudes(la.toULong(), lb.toULong()).toLong()
    return la / g * lb
}

/**
 * Returns the least common multiple of [a] and [b].
 *
 * Signs are ignored and the result is never negative. Returns `0L` when either
 * argument is zero. Throws [ArithmeticException] when the result does not fit
 * in [Long].
 */
public fun lcm(a: Long, b: Long): Long {
    val ua = magnitudeOf(a)
    val ub = magnitudeOf(b)
    if (ua == 0uL || ub == 0uL) return 0L
    val g = gcdOfMagnitudes(ua, ub)
    val quotient = ua / g
    val product = quotient * ub
    if (product / ub != quotient || product > Long.MAX_VALUE.toULong()) {
        throw ArithmeticException("lcm($a, $b) does not fit in Long.")
    }
    return product.toLong()
}

/**
 * Raises this value to the given non-negative [exponent] and returns the result as a [Long].
 *
 * Uses exponentiation by squaring with checked multiplication: any intermediate
 * or final overflow of [Long] throws [ArithmeticException] instead of wrapping.
 * Throws [IllegalArgumentException] when [exponent] is negative. `0.pow(0)` is `1L`.
 */
public fun Int.pow(exponent: Int): Long {
    require(exponent >= 0) { "Exponent must be non-negative, was $exponent." }
    var result = 1L
    var base = this.toLong()
    var remaining = exponent
    while (remaining > 0) {
        if (remaining and 1 == 1) result = multiplyChecked(result, base)
        remaining = remaining shr 1
        if (remaining > 0) base = multiplyChecked(base, base)
    }
    return result
}

/**
 * Returns the factorial of [n].
 *
 * Defined for `n` in `0..20`; `20!` is the largest factorial that fits in a
 * [Long]. Throws [IllegalArgumentException] when [n] is negative or greater
 * than 20. `factorial(0)` is `1L`.
 */
public fun factorial(n: Int): Long {
    require(n in 0..20) { "factorial is defined for n in 0..20, was $n." }
    var result = 1L
    for (i in 2..n) result *= i
    return result
}

/**
 * Returns `true` when [n] is a prime number.
 *
 * Uses deterministic trial division by 2, 3, and then candidates of the form
 * 6k plus or minus 1, so the running time is O(sqrt(n)). Values below 2,
 * including all negative values, return `false`.
 */
public fun isPrime(n: Long): Boolean {
    if (n < 2L) return false
    if (n < 4L) return true
    if (n % 2L == 0L || n % 3L == 0L) return false
    var i = 5L
    while (i <= n / i) {
        if (n % i == 0L || n % (i + 2L) == 0L) return false
        i += 6L
    }
    return true
}

/**
 * Returns `true` when this value is a positive power of two.
 *
 * Zero and negative values return `false`.
 */
public fun Int.isPowerOfTwo(): Boolean = this > 0 && (this and (this - 1)) == 0

/**
 * Returns `true` when this value is a positive power of two.
 *
 * Zero and negative values return `false`.
 */
public fun Long.isPowerOfTwo(): Boolean = this > 0L && (this and (this - 1L)) == 0L

/**
 * Returns the smallest power of two that is greater than or equal to this value.
 *
 * Values at or below 1, including negatives, return `1`. Throws
 * [ArithmeticException] when this value exceeds 2^30 (1073741824), the largest
 * power of two representable in [Int].
 */
public fun Int.nextPowerOfTwo(): Int {
    if (this <= 1) return 1
    if (this > MAX_INT_POWER_OF_TWO) {
        throw ArithmeticException("No Int power of two is greater than or equal to $this.")
    }
    return (this - 1).takeHighestOneBit() shl 1
}

/**
 * Returns the English ordinal suffix for this number: "st", "nd", "rd", or "th".
 *
 * The suffix is computed from the absolute value, so `-2` yields "nd". Values
 * ending in 11, 12, or 13 correctly yield "th" (11th, 112th, 1013th).
 */
public fun Int.ordinalSuffix(): String {
    val magnitude = abs(this.toLong())
    if ((magnitude % 100L).toInt() in 11..13) return "th"
    return when ((magnitude % 10L).toInt()) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

/**
 * Returns this number followed by its English ordinal suffix, such as "21st".
 *
 * The numeric part keeps its sign; see [ordinalSuffix] for the suffix rules.
 */
public fun Int.withOrdinalSuffix(): String = "$this${ordinalSuffix()}"

/** Converts this angle in degrees to radians. */
public fun Double.toRadians(): Double = this * PI / 180.0

/** Converts this angle in radians to degrees. */
public fun Double.toDegrees(): Double = this * 180.0 / PI

/**
 * Returns `true` when this value is within [epsilon] of [other].
 *
 * Exactly equal values, including matching infinities, return `true`. When
 * either value is NaN the result is `false`. Opposite or mismatched infinities
 * return `false` because their difference is not finite.
 */
public fun Double.isApproximately(other: Double, epsilon: Double = 1e-9): Boolean {
    if (this == other) return true
    return abs(this - other) <= epsilon
}

/**
 * Returns `true` when this value is within [epsilon] of [other].
 *
 * Exactly equal values, including matching infinities, return `true`. When
 * either value is NaN the result is `false`. Opposite or mismatched infinities
 * return `false` because their difference is not finite.
 */
public fun Float.isApproximately(other: Float, epsilon: Float = 1e-6f): Boolean {
    if (this == other) return true
    return abs(this - other) <= epsilon
}

/**
 * Returns the percentage that this value represents of [total].
 *
 * For example `25.percentOf(50)` is `50.0`. Returns `0.0` when [total] is zero
 * so callers never divide by zero.
 */
public fun Int.percentOf(total: Int): Double {
    if (total == 0) return 0.0
    return this.toDouble() / total * 100.0
}

/** Largest power of two representable in [Int], 2^30. */
private const val MAX_INT_POWER_OF_TWO: Int = 0x40000000

/** Magnitude of 2^63 as a ULong, the absolute value of [Long.MIN_VALUE]. */
private val LONG_MIN_MAGNITUDE: ULong = 9_223_372_036_854_775_808uL

/** Returns the absolute value of [value] as a [ULong], handling [Long.MIN_VALUE]. */
private fun magnitudeOf(value: Long): ULong =
    if (value == Long.MIN_VALUE) LONG_MIN_MAGNITUDE else abs(value).toULong()

/** Euclidean greatest common divisor over unsigned magnitudes. */
private fun gcdOfMagnitudes(a: ULong, b: ULong): ULong {
    var x = a
    var y = b
    while (y != 0uL) {
        val t = x % y
        x = y
        y = t
    }
    return x
}

/** Multiplies [a] by [b], throwing [ArithmeticException] on Long overflow. */
private fun multiplyChecked(a: Long, b: Long): Long {
    val result = a * b
    if (a != 0L && (result / a != b || (a == -1L && b == Long.MIN_VALUE))) {
        throw ArithmeticException("Long overflow: $a * $b.")
    }
    return result
}

/** Clamps this value into `0.0..1.0`. NaN stays NaN. */
public fun Double.clamp01(): Double = coerceIn(0.0, 1.0)

/** Clamps this value into `0f..1f`. NaN stays NaN. */
public fun Float.clamp01(): Float = coerceIn(0f, 1f)

/**
 * Returns the midpoint of this value and [other] without intermediate overflow.
 *
 * Rounds toward this value for odd differences, matching `(a + b) / 2` on
 * in-range inputs while staying correct near [Int.MAX_VALUE] and [Int.MIN_VALUE].
 */
public fun Int.midpoint(other: Int): Int = this + (other - this) / 2

/**
 * Returns the midpoint of this value and [other] without intermediate overflow.
 *
 * Rounds toward this value for odd differences, matching `(a + b) / 2` on
 * in-range inputs while staying correct near [Long.MAX_VALUE] and [Long.MIN_VALUE].
 */
public fun Long.midpoint(other: Long): Long = this + (other - this) / 2
