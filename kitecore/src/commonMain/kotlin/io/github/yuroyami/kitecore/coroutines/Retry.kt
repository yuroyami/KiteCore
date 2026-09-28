/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs [block] up to [times] attempts with exponentially growing delays between failures.
 *
 * The attempt number passed to [block] starts at 1. The first pause is
 * [initialDelay]; every following pause is the previous one multiplied by
 * [factor] and capped at [maxDelay]. A failure that [retryIf] rejects, and the
 * failure of the final attempt, are rethrown unchanged. For a fixed pause
 * between attempts, pass `factor = 1.0`.
 *
 * Cancellation: a [CancellationException] thrown by [block] or while delaying is
 * rethrown immediately, never retried, and never passed to [retryIf].
 *
 * @param times the maximum number of attempts, at least 1.
 * @param initialDelay the pause after the first failed attempt.
 * @param factor the multiplier applied to the pause after each failed attempt.
 * @param maxDelay the upper bound for any pause.
 * @param retryIf returns `true` when the given failure should be retried.
 * @throws IllegalArgumentException if [times] is less than 1, [initialDelay] or
 *   [maxDelay] is negative, or [factor] is not a positive finite number.
 */
public suspend fun <T> retryWithBackoff(
    times: Int,
    initialDelay: Duration,
    factor: Double = 2.0,
    maxDelay: Duration = Duration.INFINITE,
    retryIf: (Throwable) -> Boolean = { true },
    block: suspend (attempt: Int) -> T,
): T {
    require(times >= 1) { "times must be at least 1, was $times" }
    require(initialDelay >= Duration.ZERO) { "initialDelay must not be negative, was $initialDelay" }
    require(factor.isFinite() && factor > 0.0) { "factor must be a positive finite number, was $factor" }
    require(maxDelay >= Duration.ZERO) { "maxDelay must not be negative, was $maxDelay" }
    var nextDelay = initialDelay.coerceAtMost(maxDelay)
    for (attempt in 1..times) {
        try {
            return block(attempt)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (attempt == times || !retryIf(cause)) throw cause
        }
        delay(nextDelay)
        nextDelay = (nextDelay * factor).coerceAtMost(maxDelay)
    }
    error("retryWithBackoff exited its attempt loop without a result")
}

/**
 * Runs [block] and wraps its outcome in a [Result], keeping cancellation intact.
 *
 * This is the suspend-safe counterpart of [runCatching]: every ordinary failure
 * becomes [Result.failure], while cancellation still propagates.
 *
 * Cancellation: a [CancellationException] thrown by [block] is rethrown, never
 * captured in the returned [Result].
 */
public suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

/**
 * Runs [block] with `this` as its receiver and wraps its outcome in a [Result], keeping cancellation intact.
 *
 * This is the suspend-safe counterpart of the receiver form of [runCatching]:
 * every ordinary failure becomes [Result.failure], while cancellation still
 * propagates.
 *
 * Cancellation: a [CancellationException] thrown by [block] is rethrown, never
 * captured in the returned [Result].
 */
public suspend fun <T, R> T.runCatchingCancellable(block: suspend T.() -> R): Result<R> =
    try {
        Result.success(block())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        Result.failure(cause)
    }
