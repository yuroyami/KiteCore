/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

/**
 * Runs [block] on this receiver when [condition] is `true` and returns the receiver.
 *
 * A conditional [apply]. The receiver is returned unchanged when [condition]
 * is `false`.
 *
 * ```kotlin
 * val request = HttpRequestBuilder()
 *     .applyIf(compress) { header("Content-Encoding", "gzip") }
 * ```
 *
 * @param condition whether to run [block].
 * @param block configuration to apply to the receiver.
 * @return the receiver, for chaining.
 */
public inline fun <T> T.applyIf(condition: Boolean, block: T.() -> Unit): T {
    if (condition) block()
    return this
}

/**
 * Runs [block] on this receiver when [condition] is `false` and returns the receiver.
 *
 * The negated counterpart of [applyIf].
 *
 * @param condition whether to skip [block].
 * @param block configuration to apply to the receiver.
 * @return the receiver, for chaining.
 */
public inline fun <T> T.applyUnless(condition: Boolean, block: T.() -> Unit): T {
    if (!condition) block()
    return this
}

/**
 * Passes this receiver to [block] when [condition] is `true` and returns the receiver.
 *
 * A conditional [also], for side effects such as logging that should only
 * happen on some code paths.
 *
 * @param condition whether to run [block].
 * @param block side effect that receives the receiver as its argument.
 * @return the receiver, for chaining.
 */
public inline fun <T> T.alsoIf(condition: Boolean, block: (T) -> Unit): T {
    if (condition) block(this)
    return this
}

/**
 * Returns the result of [transform] applied to this value when [condition] is `true`, otherwise this value.
 *
 * Unlike [applyIf], the block produces a replacement value instead of mutating
 * the receiver, so it also works with immutable types.
 *
 * ```kotlin
 * val label = name.transformIf(truncate) { it.take(8) }
 * ```
 *
 * @param condition whether to apply [transform].
 * @param transform mapping from the current value to its replacement.
 * @return the transformed value, or the receiver unchanged.
 */
public inline fun <T> T.transformIf(condition: Boolean, transform: (T) -> T): T =
    if (condition) transform(this) else this

/**
 * Returns this value, or `false` when this value is `null`.
 *
 * Reads better than `?: false` in long boolean expressions.
 */
public fun Boolean?.orFalse(): Boolean = this ?: false

/**
 * Returns this value, or `true` when this value is `null`.
 *
 * Reads better than `?: true` in long boolean expressions.
 */
public fun Boolean?.orTrue(): Boolean = this ?: true

/**
 * Returns `1` when this value is `true` and `0` when it is `false`.
 *
 * Useful for counting flags, for example `flags.sumOf { it.toInt() }`.
 */
public fun Boolean.toInt(): Int = if (this) 1 else 0
