/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

/**
 * Returns a new [Pair] with the components exchanged.
 *
 * ```kotlin
 * val (value, key) = (key to value).swapped()
 * ```
 */
public fun <A, B> Pair<A, B>.swapped(): Pair<B, A> = second to first

/**
 * Returns a new [Pair] whose first component is the result of [transform] applied to the current first component.
 *
 * The second component is carried over unchanged.
 *
 * @param transform mapping applied to the first component.
 */
public inline fun <A, B, R> Pair<A, B>.mapFirst(transform: (A) -> R): Pair<R, B> =
    transform(first) to second

/**
 * Returns a new [Pair] whose second component is the result of [transform] applied to the current second component.
 *
 * The first component is carried over unchanged.
 *
 * @param transform mapping applied to the second component.
 */
public inline fun <A, B, R> Pair<A, B>.mapSecond(transform: (B) -> R): Pair<A, R> =
    first to transform(second)

/**
 * Returns a new [Pair] with both components transformed independently.
 *
 * Equivalent to `mapFirst(transformFirst).mapSecond(transformSecond)` in a
 * single call.
 *
 * @param transformFirst mapping applied to the first component.
 * @param transformSecond mapping applied to the second component.
 */
public inline fun <A, B, C, D> Pair<A, B>.mapBoth(
    transformFirst: (A) -> C,
    transformSecond: (B) -> D,
): Pair<C, D> = transformFirst(first) to transformSecond(second)

/**
 * Returns a [Triple] of this pair's components followed by [third].
 *
 * ```kotlin
 * val located = (x to y).toTriple(z)
 * ```
 *
 * @param third the value appended as the third component.
 */
public fun <A, B, C> Pair<A, B>.toTriple(third: C): Triple<A, B, C> =
    Triple(first, second, third)

/**
 * Returns a [Pair] of the second and third components, discarding the first.
 */
public fun <A, B, C> Triple<A, B, C>.dropFirst(): Pair<B, C> = second to third

/**
 * Returns a [Pair] of the first and third components, discarding the second.
 */
public fun <A, B, C> Triple<A, B, C>.dropSecond(): Pair<A, C> = first to third

/**
 * Returns a [Pair] of the first and second components, discarding the third.
 */
public fun <A, B, C> Triple<A, B, C>.dropThird(): Pair<A, B> = first to second

/**
 * Returns a new [Triple] whose first component is the result of [transform] applied to the current first component.
 *
 * The other components are carried over unchanged.
 *
 * @param transform mapping applied to the first component.
 */
public inline fun <A, B, C, R> Triple<A, B, C>.mapFirst(transform: (A) -> R): Triple<R, B, C> =
    Triple(transform(first), second, third)

/**
 * Returns a new [Triple] whose second component is the result of [transform] applied to the current second component.
 *
 * The other components are carried over unchanged.
 *
 * @param transform mapping applied to the second component.
 */
public inline fun <A, B, C, R> Triple<A, B, C>.mapSecond(transform: (B) -> R): Triple<A, R, C> =
    Triple(first, transform(second), third)

/**
 * Returns a new [Triple] whose third component is the result of [transform] applied to the current third component.
 *
 * The other components are carried over unchanged.
 *
 * @param transform mapping applied to the third component.
 */
public inline fun <A, B, C, R> Triple<A, B, C>.mapThird(transform: (C) -> R): Triple<A, B, R> =
    Triple(first, second, transform(third))
