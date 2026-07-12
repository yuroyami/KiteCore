/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.collections

import kotlin.jvm.JvmName
import kotlin.math.sqrt

// -------------------------------------------------------------------------
// Median
// -------------------------------------------------------------------------

/**
 * Returns the median of this list.
 *
 * For an even number of elements, the mean of the two middle values is returned.
 * The receiver is not modified; sorting happens on a copy in O(n log n).
 *
 * @throws NoSuchElementException if the list is empty.
 */
public fun List<Double>.median(): Double {
    if (isEmpty()) throw NoSuchElementException("Cannot compute the median of an empty list.")
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/** Returns the median of this list, or null if the list is empty. */
public fun List<Double>.medianOrNull(): Double? = if (isEmpty()) null else median()

/**
 * Returns the median of this list.
 *
 * Delegates to the [List] of [Double] overload after widening each element.
 *
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("medianOfInt")
public fun List<Int>.median(): Double = map { it.toDouble() }.median()

/** Returns the median of this list, or null if the list is empty. */
@JvmName("medianOrNullOfInt")
public fun List<Int>.medianOrNull(): Double? = if (isEmpty()) null else median()

/**
 * Returns the median of this list.
 *
 * Delegates to the [List] of [Double] overload after widening each element,
 * which loses precision above 2^53.
 *
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("medianOfLong")
public fun List<Long>.median(): Double = map { it.toDouble() }.median()

/** Returns the median of this list, or null if the list is empty. */
@JvmName("medianOrNullOfLong")
public fun List<Long>.medianOrNull(): Double? = if (isEmpty()) null else median()

// -------------------------------------------------------------------------
// Variance and standard deviation
// -------------------------------------------------------------------------

/**
 * Returns the population variance of this list.
 *
 * The divisor is the element count, not count minus one. A single-element list
 * has variance 0.0.
 *
 * @throws NoSuchElementException if the list is empty.
 */
public fun List<Double>.variance(): Double {
    if (isEmpty()) throw NoSuchElementException("Cannot compute the variance of an empty list.")
    val mean = average()
    var acc = 0.0
    for (element in this) {
        val delta = element - mean
        acc += delta * delta
    }
    return acc / size
}

/** Returns the population variance of this list, or null if the list is empty. */
public fun List<Double>.varianceOrNull(): Double? = if (isEmpty()) null else variance()

/**
 * Returns the population variance of this list.
 *
 * Delegates to the [List] of [Double] overload after widening each element.
 *
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("varianceOfInt")
public fun List<Int>.variance(): Double = map { it.toDouble() }.variance()

/** Returns the population variance of this list, or null if the list is empty. */
@JvmName("varianceOrNullOfInt")
public fun List<Int>.varianceOrNull(): Double? = if (isEmpty()) null else variance()

/**
 * Returns the population variance of this list.
 *
 * Delegates to the [List] of [Double] overload after widening each element,
 * which loses precision above 2^53.
 *
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("varianceOfLong")
public fun List<Long>.variance(): Double = map { it.toDouble() }.variance()

/** Returns the population variance of this list, or null if the list is empty. */
@JvmName("varianceOrNullOfLong")
public fun List<Long>.varianceOrNull(): Double? = if (isEmpty()) null else variance()

/**
 * Returns the population standard deviation of this list.
 *
 * Equal to the square root of [variance].
 *
 * @throws NoSuchElementException if the list is empty.
 */
public fun List<Double>.stdDev(): Double = sqrt(variance())

/** Returns the population standard deviation of this list, or null if the list is empty. */
public fun List<Double>.stdDevOrNull(): Double? = if (isEmpty()) null else stdDev()

/**
 * Returns the population standard deviation of this list.
 *
 * Delegates to the [List] of [Double] overload after widening each element.
 *
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("stdDevOfInt")
public fun List<Int>.stdDev(): Double = map { it.toDouble() }.stdDev()

/** Returns the population standard deviation of this list, or null if the list is empty. */
@JvmName("stdDevOrNullOfInt")
public fun List<Int>.stdDevOrNull(): Double? = if (isEmpty()) null else stdDev()

/**
 * Returns the population standard deviation of this list.
 *
 * Delegates to the [List] of [Double] overload after widening each element,
 * which loses precision above 2^53.
 *
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("stdDevOfLong")
public fun List<Long>.stdDev(): Double = map { it.toDouble() }.stdDev()

/** Returns the population standard deviation of this list, or null if the list is empty. */
@JvmName("stdDevOrNullOfLong")
public fun List<Long>.stdDevOrNull(): Double? = if (isEmpty()) null else stdDev()

// -------------------------------------------------------------------------
// Percentiles
// -------------------------------------------------------------------------

/**
 * Returns the [p]-th percentile of this list using linear interpolation between closest ranks.
 *
 * [p] is a percentage in the range 0.0 to 100.0; `percentile(0.0)` is the minimum,
 * `percentile(100.0)` the maximum, and `percentile(50.0)` the median. The receiver
 * is not modified; sorting happens on a copy in O(n log n).
 *
 * @throws IllegalArgumentException if [p] is not in the range 0.0 to 100.0.
 * @throws NoSuchElementException if the list is empty.
 */
public fun List<Double>.percentile(p: Double): Double {
    require(p in 0.0..100.0) { "p must be in 0..100 but was $p." }
    if (isEmpty()) throw NoSuchElementException("Cannot compute a percentile of an empty list.")
    val sorted = sorted()
    val rank = p / 100.0 * (sorted.size - 1)
    val lower = rank.toInt()
    val fraction = rank - lower
    return if (lower < sorted.lastIndex) {
        sorted[lower] + (sorted[lower + 1] - sorted[lower]) * fraction
    } else {
        sorted[lower]
    }
}

/**
 * Returns the [p]-th percentile of this list, or null if the list is empty.
 *
 * @throws IllegalArgumentException if [p] is not in the range 0.0 to 100.0.
 */
public fun List<Double>.percentileOrNull(p: Double): Double? {
    require(p in 0.0..100.0) { "p must be in 0..100 but was $p." }
    return if (isEmpty()) null else percentile(p)
}

/**
 * Returns the [p]-th percentile of this list using linear interpolation between closest ranks.
 *
 * Delegates to the [List] of [Double] overload after widening each element.
 *
 * @throws IllegalArgumentException if [p] is not in the range 0.0 to 100.0.
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("percentileOfInt")
public fun List<Int>.percentile(p: Double): Double = map { it.toDouble() }.percentile(p)

/**
 * Returns the [p]-th percentile of this list, or null if the list is empty.
 *
 * @throws IllegalArgumentException if [p] is not in the range 0.0 to 100.0.
 */
@JvmName("percentileOrNullOfInt")
public fun List<Int>.percentileOrNull(p: Double): Double? {
    require(p in 0.0..100.0) { "p must be in 0..100 but was $p." }
    return if (isEmpty()) null else percentile(p)
}

/**
 * Returns the [p]-th percentile of this list using linear interpolation between closest ranks.
 *
 * Delegates to the [List] of [Double] overload after widening each element,
 * which loses precision above 2^53.
 *
 * @throws IllegalArgumentException if [p] is not in the range 0.0 to 100.0.
 * @throws NoSuchElementException if the list is empty.
 */
@JvmName("percentileOfLong")
public fun List<Long>.percentile(p: Double): Double = map { it.toDouble() }.percentile(p)

/**
 * Returns the [p]-th percentile of this list, or null if the list is empty.
 *
 * @throws IllegalArgumentException if [p] is not in the range 0.0 to 100.0.
 */
@JvmName("percentileOrNullOfLong")
public fun List<Long>.percentileOrNull(p: Double): Double? {
    require(p in 0.0..100.0) { "p must be in 0..100 but was $p." }
    return if (isEmpty()) null else percentile(p)
}
