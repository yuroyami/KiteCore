/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] until it succeeds, at most [times] times, with a longer pause after each failure.
 *
 * Returns the first successful result. When every attempt fails, it throws the
 * failure of the last attempt, unchanged.
 *
 * ### Example
 * ```kotlin
 * val user = retryWithBackoff(
 *     times = 5,
 *     initialDelay = 100.milliseconds,
 *     maxDelay = 1.seconds,
 * ) {
 *     withTimeout(2.seconds) { api.fetchUser(id) }
 * }
 * // Pauses between the 5 attempts: 100 ms, 200 ms, 400 ms, 800 ms.
 * ```
 *
 * ### Cancellation
 * - When the caller is cancelled, the retries stop at once and the cancellation is
 *   thrown. It is never retried, and [retryIf] never sees it.
 * - A [CancellationException] from inside [block], while the caller is still active,
 *   is an ordinary failure. So a timeout of a `withTimeout` inside [block] is retried,
 *   as in the example.
 *
 * @param times the maximum number of attempts, first attempt included. At least 1.
 *   `times = 3` means one attempt and up to two retries.
 * @param initialDelay the pause after the first failure. Zero or more.
 * @param factor each pause is the previous pause times [factor]. `2.0` doubles it.
 *   `1.0` keeps a fixed pause. Must be a positive finite number.
 * @param maxDelay the longest pause. Zero or more. The default means no limit.
 * @param retryIf returns `true` for a failure that should be retried. A failure that it
 *   rejects is thrown at once. The default retries every failure.
 * @param block the work. Its parameter is the attempt number, starting at 1.
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
        } catch (cause: Throwable) {
            // Only the caller's own cancellation stops the loop.
            currentCoroutineContext().ensureActive()
            if (attempt == times || !retryIf(cause)) throw cause
        }
        delay(nextDelay)
        nextDelay = (nextDelay * factor).coerceAtMost(maxDelay)
    }
    error("retryWithBackoff exited its attempt loop without a result")
}

/**
 * Like [runCatching], but safe in suspend code: it never swallows the cancellation of the caller.
 *
 * Returns [Result.success] with the value of [block], or [Result.failure] with its
 * exception. Use it wherever you would write `runCatching` around a suspend call.
 *
 * ### Example
 * ```kotlin
 * val state = runCatchingCancellable { api.fetchDashboard() }.fold(
 *     onSuccess = { DashboardState.Content(it) },
 *     onFailure = { DashboardState.Error(it) },
 * )
 * ```
 *
 * ### Compared with `runCatching`
 * `runCatching` also catches the `CancellationException` of a cancelled coroutine. The
 * coroutine then keeps running after its scope was cancelled, for example on a screen
 * that the user already left. `runCatchingCancellable` throws that cancellation on.
 *
 * ### Cancellation
 * - When the caller is cancelled, the cancellation is thrown, even when [block]
 *   turned it into another exception.
 * - A [CancellationException] from inside [block], while the caller is still active,
 *   becomes [Result.failure]. Examples: the timeout of a `withTimeout` inside [block],
 *   or `await()` on a `Deferred` that other code cancelled.
 *
 * @see retryWithBackoff
 */
public suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cause: Throwable) {
        currentCoroutineContext().ensureActive()
        Result.failure(cause)
    }

/**
 * The receiver form of [runCatchingCancellable]: [block] runs with `this` as its receiver.
 *
 * ```kotlin
 * val config: Result<Config> =
 *     rawJson.runCatchingCancellable { parseConfig(this) }
 * ```
 *
 * The cancellation rules are the same as for the form without a receiver.
 */
public suspend fun <T, R> T.runCatchingCancellable(block: suspend T.() -> R): Result<R> =
    try {
        Result.success(block())
    } catch (cause: Throwable) {
        currentCoroutineContext().ensureActive()
        Result.failure(cause)
    }
