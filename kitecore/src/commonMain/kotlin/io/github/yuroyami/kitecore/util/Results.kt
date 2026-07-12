/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

/**
 * Runs [block] and returns its value, or `null` when it throws an [Exception].
 *
 * Only [Exception] is caught, not [Throwable], so [Error]s and other
 * non-Exception throwables propagate to the caller. This function is not
 * suspending: `CancellationException` is an [Exception] and would be swallowed
 * here, so do not wrap suspending calls in it. Suspend-aware variants exist
 * elsewhere in this library as `runCatchingCancellable`.
 *
 * ```kotlin
 * val parsed: Int? = tryOrNull { text.toInt() }
 * ```
 *
 * @param block computation to attempt.
 * @return the value of [block], or `null` when it threw an [Exception].
 */
public inline fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (ignored: Exception) {
        null
    }

/**
 * Runs [block] and returns its value, or [default] when it throws an [Exception].
 *
 * Only [Exception] is caught, not [Throwable], so [Error]s and other
 * non-Exception throwables propagate to the caller. This function is not
 * suspending: `CancellationException` is an [Exception] and would be swallowed
 * here, so do not wrap suspending calls in it. Suspend-aware variants exist
 * elsewhere in this library as `runCatchingCancellable`.
 *
 * @param default value returned when [block] throws an [Exception].
 * @param block computation to attempt.
 * @return the value of [block], or [default] when it threw an [Exception].
 */
public inline fun <T> tryOrDefault(default: T, block: () -> T): T =
    try {
        block()
    } catch (ignored: Exception) {
        default
    }

/**
 * Runs [block] and returns its value, or the value of [fallback] applied to the thrown [Exception].
 *
 * Use this instead of [tryOrDefault] when the fallback needs the exception,
 * for example to log it or derive a value from it. Only [Exception] is caught,
 * not [Throwable], so [Error]s and other non-Exception throwables propagate to
 * the caller. This function is not suspending: `CancellationException` is an
 * [Exception] and would be swallowed here, so do not wrap suspending calls in
 * it. Suspend-aware variants exist elsewhere in this library as
 * `runCatchingCancellable`.
 *
 * @param fallback recovery that maps the caught [Exception] to a result value.
 * @param block computation to attempt.
 * @return the value of [block], or the value of [fallback] when it threw.
 */
public inline fun <T> tryOrElse(fallback: (Exception) -> T, block: () -> T): T =
    try {
        block()
    } catch (exception: Exception) {
        fallback(exception)
    }

/**
 * Returns the result of [transform] applied to the encapsulated value, or the original failure.
 *
 * The monadic counterpart of [Result.map] for transforms that themselves
 * produce a [Result], avoiding the nested `Result<Result<R>>`. Exceptions
 * thrown by [transform] are not caught, matching [Result.map] rather than
 * [Result.mapCatching].
 *
 * ```kotlin
 * val port: Result<Int> = readConfig().flatMap { parsePort(it) }
 * ```
 *
 * @param transform mapping from the success value to the next [Result].
 * @return the [Result] produced by [transform], or this failure unchanged.
 */
public inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(
        onSuccess = { transform(it) },
        onFailure = { Result.failure(it) },
    )

/**
 * Returns the inner [Result], collapsing one level of nesting.
 *
 * An outer failure stays a failure; an outer success unwraps to the inner
 * [Result], whether that inner value is a success or a failure.
 */
public fun <T> Result<Result<T>>.flatten(): Result<T> =
    fold(
        onSuccess = { it },
        onFailure = { Result.failure(it) },
    )

/**
 * Returns a [Result] whose failure exception is replaced by the result of [transform], or the original success.
 *
 * The failure-side counterpart of [Result.map], typically used to wrap
 * low-level exceptions into domain-specific ones. Exceptions thrown by
 * [transform] are not caught.
 *
 * ```kotlin
 * val user = fetchUser(id).mapFailure { UserLoadException(id, cause = it) }
 * ```
 *
 * @param transform mapping from the current exception to its replacement.
 * @return this success unchanged, or a failure holding the transformed exception.
 */
public inline fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> {
    val exception = exceptionOrNull() ?: return this
    return Result.failure(transform(exception))
}

/**
 * Combines this [Result] with [other] via [combine], failing when either is a failure.
 *
 * When both are failures the first failure wins: this receiver's exception is
 * returned and [other]'s exception is dropped. [combine] runs only when both
 * are successes, and exceptions it throws are not caught.
 *
 * ```kotlin
 * val profile = loadUser(id).zip(loadAvatar(id)) { user, avatar -> Profile(user, avatar) }
 * ```
 *
 * @param other the second [Result] to combine with.
 * @param combine mapping from both success values to the combined value.
 * @return a success holding the combined value, or the first failure.
 */
public inline fun <A, B, R> Result<A>.zip(other: Result<B>, combine: (A, B) -> R): Result<R> {
    val firstFailure = exceptionOrNull()
    if (firstFailure != null) return Result.failure(firstFailure)
    val secondFailure = other.exceptionOrNull()
    if (secondFailure != null) return Result.failure(secondFailure)
    return Result.success(combine(getOrThrow(), other.getOrThrow()))
}

/**
 * Combines this [Result] with [second] and [third] via [combine], failing when any is a failure.
 *
 * The earliest failure in declaration order wins; later exceptions are
 * dropped. [combine] runs only when all three are successes, and exceptions it
 * throws are not caught.
 *
 * @param second the second [Result] to combine with.
 * @param third the third [Result] to combine with.
 * @param combine mapping from all three success values to the combined value.
 * @return a success holding the combined value, or the earliest failure.
 */
public inline fun <A, B, C, R> Result<A>.zip(
    second: Result<B>,
    third: Result<C>,
    combine: (A, B, C) -> R,
): Result<R> {
    val firstFailure = exceptionOrNull()
    if (firstFailure != null) return Result.failure(firstFailure)
    val secondFailure = second.exceptionOrNull()
    if (secondFailure != null) return Result.failure(secondFailure)
    val thirdFailure = third.exceptionOrNull()
    if (thirdFailure != null) return Result.failure(thirdFailure)
    return Result.success(combine(getOrThrow(), second.getOrThrow(), third.getOrThrow()))
}

/**
 * Returns `true` when this [Result] is a success whose value satisfies [predicate].
 *
 * Returns `false` for failures without invoking [predicate]. Mirrors Rust's
 * `Result::is_ok_and`.
 *
 * ```kotlin
 * if (response.isSuccessAnd { it.code == 200 }) proceed()
 * ```
 *
 * @param predicate test applied to the success value.
 * @return whether this is a success that satisfies [predicate].
 */
public inline fun <T> Result<T>.isSuccessAnd(predicate: (T) -> Boolean): Boolean =
    isSuccess && predicate(getOrThrow())

/**
 * Returns `true` when this [Result] is a failure whose exception satisfies [predicate].
 *
 * Returns `false` for successes without invoking [predicate]. Mirrors Rust's
 * `Result::is_err_and`.
 *
 * @param predicate test applied to the failure exception.
 * @return whether this is a failure that satisfies [predicate].
 */
public inline fun <T> Result<T>.isFailureAnd(predicate: (Throwable) -> Boolean): Boolean {
    val exception = exceptionOrNull()
    return exception != null && predicate(exception)
}
