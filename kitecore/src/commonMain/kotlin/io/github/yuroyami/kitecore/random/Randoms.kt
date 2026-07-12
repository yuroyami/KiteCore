/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.random

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Predefined alphabet strings for random text generation.
 *
 * Each constant is an ordered string of distinct characters that can be passed
 * to [nextString] or any other API that accepts an alphabet.
 */
public object RandomAlphabets {

    /** Lowercase letters, uppercase letters, and decimal digits (a-zA-Z0-9), 62 characters. */
    public const val ALPHANUMERIC: String =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** Lowercase and uppercase Latin letters (a-zA-Z), 52 characters. */
    public const val ALPHABETIC: String =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** Decimal digits (0-9), 10 characters. */
    public const val DIGITS: String = "0123456789"

    /** Lowercase hexadecimal digits (0-9a-f), 16 characters. */
    public const val HEX_LOWER: String = "0123456789abcdef"
}

/**
 * Returns one character drawn uniformly at random from [alphabet].
 *
 * Every position in [alphabet] is equally likely, so characters that occur
 * more than once in [alphabet] are proportionally more likely to be returned.
 *
 * @param alphabet the characters to choose from.
 * @throws IllegalArgumentException if [alphabet] is empty.
 */
public fun Random.nextChar(alphabet: String): Char {
    require(alphabet.isNotEmpty()) { "alphabet must not be empty." }
    return alphabet[nextInt(alphabet.length)]
}

/**
 * Returns a random string of exactly [length] characters drawn from [alphabet].
 *
 * Each character is chosen independently and uniformly at random from the
 * positions of [alphabet]. A [length] of zero yields the empty string.
 *
 * @param length the number of characters to generate, must be non-negative.
 * @param alphabet the characters to choose from.
 * @throws IllegalArgumentException if [length] is negative or [alphabet] is empty.
 */
public fun Random.nextString(length: Int, alphabet: String): String {
    require(length >= 0) { "length must be non-negative but was $length." }
    require(alphabet.isNotEmpty()) { "alphabet must not be empty." }
    if (length == 0) return ""
    val builder = StringBuilder(length)
    repeat(length) {
        builder.append(alphabet[nextInt(alphabet.length)])
    }
    return builder.toString()
}

/**
 * Returns a random string of exactly [length] alphanumeric characters (a-zA-Z0-9).
 *
 * Each character is chosen independently and uniformly at random from
 * [RandomAlphabets.ALPHANUMERIC]. A [length] of zero yields the empty string.
 *
 * @param length the number of characters to generate, must be non-negative.
 * @throws IllegalArgumentException if [length] is negative.
 */
public fun Random.nextAlphanumeric(length: Int): String =
    nextString(length, RandomAlphabets.ALPHANUMERIC)

/**
 * Returns a random string of exactly [length] Latin letters (a-zA-Z).
 *
 * Each character is chosen independently and uniformly at random from
 * [RandomAlphabets.ALPHABETIC]. A [length] of zero yields the empty string.
 *
 * @param length the number of characters to generate, must be non-negative.
 * @throws IllegalArgumentException if [length] is negative.
 */
public fun Random.nextAlphabetic(length: Int): String =
    nextString(length, RandomAlphabets.ALPHABETIC)

/**
 * Returns a random string of exactly [length] decimal digits (0-9).
 *
 * Each digit is chosen independently and uniformly at random from
 * [RandomAlphabets.DIGITS], so leading zeros are possible. A [length] of zero
 * yields the empty string.
 *
 * @param length the number of digits to generate, must be non-negative.
 * @throws IllegalArgumentException if [length] is negative.
 */
public fun Random.nextDigits(length: Int): String =
    nextString(length, RandomAlphabets.DIGITS)

/**
 * Returns a random string of exactly [length] lowercase hexadecimal digits (0-9a-f).
 *
 * Each digit is chosen independently and uniformly at random from
 * [RandomAlphabets.HEX_LOWER]. A [length] of zero yields the empty string.
 *
 * @param length the number of hex digits to generate, must be non-negative.
 * @throws IllegalArgumentException if [length] is negative.
 */
public fun Random.nextHexString(length: Int): String =
    nextString(length, RandomAlphabets.HEX_LOWER)

/**
 * Returns `true` with the given [probability] and `false` otherwise.
 *
 * A [probability] of `0.0` always yields `false` and a [probability] of `1.0`
 * always yields `true`; values in between follow a Bernoulli distribution.
 *
 * @param probability the chance of returning `true`, must be within `0.0..1.0`.
 * @throws IllegalArgumentException if [probability] is NaN or outside `0.0..1.0`.
 */
public fun Random.nextBoolean(probability: Double): Boolean {
    require(probability in 0.0..1.0) {
        "probability must be within 0.0..1.0 but was $probability."
    }
    return nextDouble() < probability
}

/**
 * Returns a normally distributed value with the given [mean] and [standardDeviation].
 *
 * The value is produced with the Box-Muller transform, so results follow a
 * Gaussian distribution. A [standardDeviation] of `0.0` always yields [mean].
 *
 * @param mean the center of the distribution, `0.0` by default.
 * @param standardDeviation the spread of the distribution, `1.0` by default, must be non-negative.
 * @throws IllegalArgumentException if [standardDeviation] is negative or NaN.
 */
public fun Random.nextGaussian(mean: Double = 0.0, standardDeviation: Double = 1.0): Double {
    require(standardDeviation >= 0.0) {
        "standardDeviation must be non-negative but was $standardDeviation."
    }
    var u1 = nextDouble()
    while (u1 == 0.0) {
        u1 = nextDouble()
    }
    val u2 = nextDouble()
    val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    return mean + z * standardDeviation
}

/**
 * Returns an infinite lazy sequence of normally distributed values.
 *
 * Every element is drawn independently via [nextGaussian] with the given
 * [mean] and [standardDeviation]. The sequence never terminates, so callers
 * must bound it with operators such as `take`.
 *
 * @param mean the center of the distribution, `0.0` by default.
 * @param standardDeviation the spread of the distribution, `1.0` by default, must be non-negative.
 * @throws IllegalArgumentException if [standardDeviation] is negative or NaN.
 */
public fun Random.gaussianSequence(mean: Double = 0.0, standardDeviation: Double = 1.0): Sequence<Double> {
    require(standardDeviation >= 0.0) {
        "standardDeviation must be non-negative but was $standardDeviation."
    }
    return generateSequence { nextGaussian(mean, standardDeviation) }
}

/**
 * Returns `+1` or `-1` with equal probability.
 *
 * Each outcome has probability `0.5`, making this a fair sign flip.
 */
public fun Random.nextSign(): Int = if (nextBoolean()) 1 else -1

/**
 * Returns [n] elements sampled uniformly at random without replacement.
 *
 * Each element position of this collection is used at most once, so the
 * result has no repeated positions; repeated values can still appear when the
 * source itself contains duplicates. Every subset of size [n] is equally
 * likely. The relative order of the result is randomized.
 *
 * @param n the number of elements to sample, must be within `0..size`.
 * @param random the source of randomness, [Random.Default] by default.
 * @throws IllegalArgumentException if [n] is negative or greater than the collection size.
 */
public fun <T> Collection<T>.sample(n: Int, random: Random = Random.Default): List<T> {
    require(n >= 0) { "n must be non-negative but was $n." }
    require(n <= size) { "Cannot sample $n elements from a collection of size $size." }
    if (n == 0) return emptyList()
    val pool = toMutableList()
    for (i in 0 until n) {
        val j = random.nextInt(i, pool.size)
        val held = pool[i]
        pool[i] = pool[j]
        pool[j] = held
    }
    return pool.take(n)
}

/**
 * Returns [n] elements sampled without replacement, or `null` when [n] is out of range.
 *
 * Behaves like [sample] for valid arguments and returns `null` instead of
 * throwing when [n] is negative or greater than the collection size.
 *
 * @param n the number of elements to sample.
 * @param random the source of randomness, [Random.Default] by default.
 */
public fun <T> Collection<T>.sampleOrNull(n: Int, random: Random = Random.Default): List<T>? =
    if (n < 0 || n > size) null else sample(n, random)

/**
 * Returns [n] elements sampled uniformly at random with replacement.
 *
 * Each element is chosen independently, so the same position can be picked
 * multiple times and [n] may exceed the collection size. An [n] of zero
 * yields the empty list even for an empty collection.
 *
 * @param n the number of elements to sample, must be non-negative.
 * @param random the source of randomness, [Random.Default] by default.
 * @throws IllegalArgumentException if [n] is negative, or if the collection is empty while [n] is positive.
 */
public fun <T> Collection<T>.sampleWithReplacement(n: Int, random: Random = Random.Default): List<T> {
    require(n >= 0) { "n must be non-negative but was $n." }
    if (n == 0) return emptyList()
    require(isNotEmpty()) { "Cannot sample $n elements with replacement from an empty collection." }
    val source = toList()
    return List(n) { source[random.nextInt(source.size)] }
}

/**
 * Returns a random index of this list chosen with probability proportional to [weights].
 *
 * The probability of index `i` equals `weights[i] / weights.sum()`. Indices
 * whose weight is `0.0` are never chosen.
 *
 * @param weights one non-negative finite weight per element of this list.
 * @param random the source of randomness, [Random.Default] by default.
 * @throws IllegalArgumentException if the list is empty, if [weights] and the list differ in size,
 * if any weight is negative, NaN, or infinite, or if the total weight is not strictly positive.
 */
public fun <T> List<T>.weightedRandomIndex(weights: List<Double>, random: Random = Random.Default): Int {
    require(isNotEmpty()) { "Cannot pick an index of an empty list." }
    require(weights.size == size) {
        "weights has ${weights.size} entries but the list has $size elements."
    }
    var total = 0.0
    for (weight in weights) {
        require(weight >= 0.0 && weight.isFinite()) {
            "Every weight must be a non-negative finite number but found $weight."
        }
        total += weight
    }
    require(total > 0.0 && total.isFinite()) {
        "The total weight must be a positive finite number but was $total."
    }
    var remaining = random.nextDouble() * total
    for (i in indices) {
        remaining -= weights[i]
        if (remaining < 0.0) return i
    }
    for (i in indices.reversed()) {
        if (weights[i] > 0.0) return i
    }
    return lastIndex
}

/**
 * Returns a random element of this list chosen with probability proportional to [weights].
 *
 * The probability of element `i` equals `weights[i] / weights.sum()`.
 * Elements whose weight is `0.0` are never chosen.
 *
 * @param weights one non-negative finite weight per element of this list.
 * @param random the source of randomness, [Random.Default] by default.
 * @throws IllegalArgumentException if the list is empty, if [weights] and the list differ in size,
 * if any weight is negative, NaN, or infinite, or if the total weight is not strictly positive.
 */
public fun <T> List<T>.weightedRandom(weights: List<Double>, random: Random = Random.Default): T =
    this[weightedRandomIndex(weights, random)]

/**
 * Returns a weighted random element, or `null` when the arguments are invalid.
 *
 * Behaves like [weightedRandom] for valid arguments and returns `null`
 * instead of throwing when the list is empty, when [weights] and the list
 * differ in size, when any weight is negative, NaN, or infinite, or when the
 * total weight is not strictly positive.
 *
 * @param weights one non-negative finite weight per element of this list.
 * @param random the source of randomness, [Random.Default] by default.
 */
public fun <T> List<T>.weightedRandomOrNull(weights: List<Double>, random: Random = Random.Default): T? {
    if (isEmpty() || weights.size != size) return null
    var total = 0.0
    for (weight in weights) {
        if (!(weight >= 0.0 && weight.isFinite())) return null
        total += weight
    }
    if (!(total > 0.0 && total.isFinite())) return null
    return this[weightedRandomIndex(weights, random)]
}

/**
 * Returns one of [options] chosen uniformly at random.
 *
 * Every argument is equally likely to be returned. The [random] parameter
 * follows the vararg, so it must be passed by name.
 *
 * @param options the candidate values, at least one is required.
 * @param random the source of randomness, [Random.Default] by default.
 * @throws IllegalArgumentException if [options] is empty.
 */
public fun <T> randomOf(vararg options: T, random: Random = Random.Default): T {
    require(options.isNotEmpty()) { "randomOf requires at least one option." }
    return options[random.nextInt(options.size)]
}
