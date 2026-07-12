/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout

/**
 * Runs [block] up to [times] attempts and returns the first successful result.
 *
 * The attempt number passed to [block] starts at 1. After a failed attempt this
 * function suspends for [delayBetween], then tries again. The failure of the
 * final attempt is rethrown unchanged.
 *
 * Cancellation: a [CancellationException] thrown by [block] or while delaying is
 * rethrown immediately and is never retried.
 *
 * @param times the maximum number of attempts, at least 1.
 * @param delayBetween the pause between consecutive attempts.
 * @throws IllegalArgumentException if [times] is less than 1 or [delayBetween] is negative.
 */
public suspend fun <T> retry(
    times: Int,
    delayBetween: Duration = Duration.ZERO,
    block: suspend (attempt: Int) -> T,
): T {
    require(times >= 1) { "times must be at least 1, was $times" }
    require(delayBetween >= Duration.ZERO) { "delayBetween must not be negative, was $delayBetween" }
    for (attempt in 1..times) {
        try {
            return block(attempt)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (attempt == times) throw cause
        }
        delay(delayBetween)
    }
    error("retry exited its attempt loop without a result")
}

/**
 * Runs [block] up to [times] attempts with exponentially growing delays between failures.
 *
 * The attempt number passed to [block] starts at 1. The first pause is
 * [initialDelay]; every following pause is the previous one multiplied by
 * [factor] and capped at [maxDelay]. A failure that [retryIf] rejects, and the
 * failure of the final attempt, are rethrown unchanged.
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
 * Runs [block] repeatedly until it succeeds and returns that first successful result.
 *
 * The attempt number passed to [block] starts at 1 and saturates at
 * [Int.MAX_VALUE]. After a failure that [retryIf] accepts, this function
 * suspends for [delayBetween] and tries again. A failure that [retryIf] rejects
 * is rethrown unchanged.
 *
 * Cancellation: a [CancellationException] thrown by [block] or while delaying is
 * rethrown immediately and never passed to [retryIf]; cancelling the calling
 * coroutine is the only way to stop an endlessly failing loop.
 *
 * @param delayBetween the pause between consecutive attempts.
 * @param retryIf returns `true` when the given failure should be retried.
 * @throws IllegalArgumentException if [delayBetween] is negative.
 */
public suspend fun <T> retryForever(
    delayBetween: Duration = Duration.ZERO,
    retryIf: (Throwable) -> Boolean = { true },
    block: suspend (attempt: Int) -> T,
): T {
    require(delayBetween >= Duration.ZERO) { "delayBetween must not be negative, was $delayBetween" }
    var attempt = 1
    while (true) {
        try {
            return block(attempt)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (!retryIf(cause)) throw cause
        }
        delay(delayBetween)
        if (attempt < Int.MAX_VALUE) attempt++
    }
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

/**
 * Runs [block] with the given [timeout] and returns [default] if the timeout elapses first.
 *
 * A non-positive [timeout] returns [default] without running [block]. Failures
 * thrown by [block] propagate unchanged.
 *
 * Cancellation: on timeout, [block] is cancelled and [default] is returned.
 * Cancellation of the calling coroutine is rethrown and is never converted
 * into [default].
 *
 * @param timeout how long [block] may run before it is cancelled.
 * @param default the value returned when the timeout elapses.
 */
public suspend fun <T> withTimeoutOrDefault(
    timeout: Duration,
    default: T,
    block: suspend CoroutineScope.() -> T,
): T =
    try {
        withTimeout(timeout) { block() }
    } catch (cause: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        default
    }

/**
 * Runs every block concurrently and returns the result of the first one to complete.
 *
 * The remaining blocks are cancelled as soon as a winner exists. If the first
 * block to complete fails, that failure is rethrown and the remaining blocks
 * are cancelled.
 *
 * Cancellation: cancelling the calling coroutine cancels every block. Losing
 * blocks always receive cancellation; a winner is never cancelled by this
 * function.
 *
 * @throws IllegalArgumentException if [blocks] is empty.
 */
public suspend fun <T> raceOf(vararg blocks: suspend CoroutineScope.() -> T): T {
    require(blocks.isNotEmpty()) { "raceOf requires at least one block" }
    return coroutineScope {
        val contenders = blocks.map { block -> async { block() } }
        val winner = select<T> {
            for (contender in contenders) {
                contender.onAwait { it }
            }
        }
        for (contender in contenders) {
            contender.cancel()
        }
        winner
    }
}

/**
 * Runs every block concurrently and returns the result of the first one to succeed.
 *
 * Unlike [raceOf], a failing block does not decide the race; its failure is
 * recorded and the remaining blocks keep running. The remaining blocks are
 * cancelled as soon as one succeeds. If every block fails, the last observed
 * failure is rethrown.
 *
 * Cancellation: cancelling the calling coroutine cancels every block. A block
 * that throws [CancellationException] counts as failed for the race but its
 * cancellation is not swallowed inside that block's coroutine.
 *
 * @throws IllegalArgumentException if [blocks] is empty.
 */
public suspend fun <T> firstSuccessOf(vararg blocks: suspend CoroutineScope.() -> T): T {
    require(blocks.isNotEmpty()) { "firstSuccessOf requires at least one block" }
    return coroutineScope {
        val outcomes = Channel<Result<T>>(capacity = blocks.size)
        val contenders = blocks.map { block ->
            launch {
                val outcome = try {
                    Result.success(block())
                } catch (cause: CancellationException) {
                    outcomes.trySend(Result.failure(cause))
                    throw cause
                } catch (cause: Throwable) {
                    Result.failure(cause)
                }
                outcomes.trySend(outcome)
            }
        }
        var lastFailure: Throwable? = null
        var remaining = blocks.size
        while (remaining > 0) {
            val outcome = outcomes.receive()
            remaining--
            if (outcome.isSuccess) {
                for (contender in contenders) {
                    contender.cancel()
                }
                return@coroutineScope outcome.getOrThrow()
            }
            lastFailure = outcome.exceptionOrNull()
        }
        throw lastFailure ?: error("firstSuccessOf received no outcomes")
    }
}
