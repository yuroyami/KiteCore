/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Transforms every element concurrently and returns the results in the original order.
 *
 * At most [concurrency] transforms run at the same time, limited by a
 * [Semaphore]. The first failing transform cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * transform before rethrowing.
 *
 * @param concurrency the maximum number of concurrent transforms, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T, R> Iterable<T>.parallelMap(
    concurrency: Int = Int.MAX_VALUE,
    transform: suspend (T) -> R,
): List<R> {
    require(concurrency >= 1) { "concurrency must be at least 1, was $concurrency" }
    return coroutineScope {
        val limiter = Semaphore(concurrency)
        map { element ->
            async { limiter.withPermit { transform(element) } }
        }.awaitAll()
    }
}

/**
 * Transforms every element with its index concurrently and returns the results in the original order.
 *
 * At most [concurrency] transforms run at the same time, limited by a
 * [Semaphore]. The first failing transform cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * transform before rethrowing.
 *
 * @param concurrency the maximum number of concurrent transforms, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T, R> Iterable<T>.parallelMapIndexed(
    concurrency: Int = Int.MAX_VALUE,
    transform: suspend (index: Int, element: T) -> R,
): List<R> {
    require(concurrency >= 1) { "concurrency must be at least 1, was $concurrency" }
    return coroutineScope {
        val limiter = Semaphore(concurrency)
        mapIndexed { index, element ->
            async { limiter.withPermit { transform(index, element) } }
        }.awaitAll()
    }
}

/**
 * Transforms every element concurrently and returns the non-null results in the original order.
 *
 * At most [concurrency] transforms run at the same time, limited by a
 * [Semaphore]. The first failing transform cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * transform before rethrowing.
 *
 * @param concurrency the maximum number of concurrent transforms, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T, R : Any> Iterable<T>.parallelMapNotNull(
    concurrency: Int = Int.MAX_VALUE,
    transform: suspend (T) -> R?,
): List<R> = parallelMap(concurrency, transform).filterNotNull()

/**
 * Transforms every element with its index concurrently and returns the non-null results in the original order.
 *
 * At most [concurrency] transforms run at the same time, limited by a
 * [Semaphore]. The first failing transform cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * transform before rethrowing.
 *
 * @param concurrency the maximum number of concurrent transforms, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T, R : Any> Iterable<T>.parallelMapIndexedNotNull(
    concurrency: Int = Int.MAX_VALUE,
    transform: suspend (index: Int, element: T) -> R?,
): List<R> = parallelMapIndexed(concurrency, transform).filterNotNull()

/**
 * Transforms every element concurrently and returns the flattened results in the original order.
 *
 * At most [concurrency] transforms run at the same time, limited by a
 * [Semaphore]. The first failing transform cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * transform before rethrowing.
 *
 * @param concurrency the maximum number of concurrent transforms, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T, R> Iterable<T>.parallelFlatMap(
    concurrency: Int = Int.MAX_VALUE,
    transform: suspend (T) -> Iterable<R>,
): List<R> = parallelMap(concurrency, transform).flatten()

/**
 * Runs [action] for every element concurrently and returns when all have finished.
 *
 * At most [concurrency] actions run at the same time, limited by a
 * [Semaphore]. The first failing action cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * action before rethrowing.
 *
 * @param concurrency the maximum number of concurrent actions, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T> Iterable<T>.parallelForEach(
    concurrency: Int = Int.MAX_VALUE,
    action: suspend (T) -> Unit,
) {
    require(concurrency >= 1) { "concurrency must be at least 1, was $concurrency" }
    coroutineScope {
        val limiter = Semaphore(concurrency)
        for (element in this@parallelForEach) {
            launch { limiter.withPermit { action(element) } }
        }
    }
}

/**
 * Runs [action] for every element with its index concurrently and returns when all have finished.
 *
 * At most [concurrency] actions run at the same time, limited by a
 * [Semaphore]. The first failing action cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * action before rethrowing.
 *
 * @param concurrency the maximum number of concurrent actions, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T> Iterable<T>.parallelForEachIndexed(
    concurrency: Int = Int.MAX_VALUE,
    action: suspend (index: Int, element: T) -> Unit,
) {
    require(concurrency >= 1) { "concurrency must be at least 1, was $concurrency" }
    coroutineScope {
        val limiter = Semaphore(concurrency)
        forEachIndexed { index, element ->
            launch { limiter.withPermit { action(index, element) } }
        }
    }
}

/**
 * Evaluates [predicate] for every element concurrently and returns the matching elements in the original order.
 *
 * At most [concurrency] predicates run at the same time, limited by a
 * [Semaphore]. The first failing predicate cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * predicate before rethrowing.
 *
 * @param concurrency the maximum number of concurrent predicate evaluations, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T> Iterable<T>.parallelFilter(
    concurrency: Int = Int.MAX_VALUE,
    predicate: suspend (T) -> Boolean,
): List<T> {
    val decisions = parallelMap(concurrency) { element -> element to predicate(element) }
    return buildList {
        for ((element, keep) in decisions) {
            if (keep) add(element)
        }
    }
}

/**
 * Evaluates [predicate] for every element concurrently and returns the non-matching elements in the original order.
 *
 * At most [concurrency] predicates run at the same time, limited by a
 * [Semaphore]. The first failing predicate cancels the remaining work and its
 * failure is rethrown to the caller.
 *
 * Cancellation: cancelling the calling coroutine cancels every in-flight
 * predicate before rethrowing.
 *
 * @param concurrency the maximum number of concurrent predicate evaluations, at least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public suspend fun <T> Iterable<T>.parallelFilterNot(
    concurrency: Int = Int.MAX_VALUE,
    predicate: suspend (T) -> Boolean,
): List<T> = parallelFilter(concurrency) { element -> !predicate(element) }

/**
 * Awaits every deferred value and returns each outcome as a [Result], in the original order.
 *
 * Unlike [awaitAll], this function never throws for a failed or cancelled
 * deferred; the failure is captured in the corresponding [Result] and the
 * remaining deferred values are still awaited.
 *
 * Cancellation: cancelling the calling coroutine rethrows its
 * [CancellationException]; only cancellation that belongs to one of the
 * deferred values is captured as a failed [Result].
 */
public suspend fun <T> List<Deferred<T>>.awaitAllSettled(): List<Result<T>> =
    map { deferred ->
        try {
            Result.success(deferred.await())
        } catch (cause: CancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(cause)
        } catch (cause: Throwable) {
            Result.failure(cause)
        }
    }

/**
 * Awaits this deferred value and returns `null` if [timeout] elapses first.
 *
 * A non-positive [timeout] returns `null` without waiting. If the deferred
 * fails, its failure is rethrown unchanged; `null` signals only a timeout.
 *
 * Cancellation: on timeout, only the wait is cancelled, never the deferred
 * itself. Cancellation of the calling coroutine, and cancellation of the
 * deferred, are rethrown.
 *
 * @param timeout how long to wait for the value.
 */
public suspend fun <T> Deferred<T>.awaitOrNull(timeout: Duration): T? =
    try {
        withTimeout(timeout) { await() }
    } catch (cause: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        null
    }

/**
 * Awaits this deferred value and returns [default] if [timeout] elapses first.
 *
 * A non-positive [timeout] returns [default] without waiting. If the deferred
 * fails, its failure is rethrown unchanged; [default] signals only a timeout.
 *
 * Cancellation: on timeout, only the wait is cancelled, never the deferred
 * itself. Cancellation of the calling coroutine, and cancellation of the
 * deferred, are rethrown.
 *
 * @param timeout how long to wait for the value.
 * @param default the value returned when the timeout elapses.
 */
public suspend fun <T> Deferred<T>.awaitOrDefault(timeout: Duration, default: T): T =
    try {
        withTimeout(timeout) { await() }
    } catch (cause: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        default
    }

/**
 * Launches a coroutine that runs [action] repeatedly with a fixed pause between runs.
 *
 * The first run starts after [initialDelay]; each following run starts
 * [interval] after the previous run finished. A failure thrown by [action]
 * stops the loop and propagates through the returned [Job] to the scope.
 *
 * Cancellation: cancelling the returned [Job], or the scope, stops the loop at
 * the next suspension point.
 *
 * @param interval the pause between the end of one run and the start of the next.
 * @param initialDelay the pause before the first run.
 * @throws IllegalArgumentException if [interval] is not positive or [initialDelay] is negative.
 */
public fun CoroutineScope.launchPeriodic(
    interval: Duration,
    initialDelay: Duration = Duration.ZERO,
    action: suspend () -> Unit,
): Job {
    require(interval > Duration.ZERO) { "interval must be positive, was $interval" }
    require(initialDelay >= Duration.ZERO) { "initialDelay must not be negative, was $initialDelay" }
    return launch {
        delay(initialDelay)
        while (true) {
            action()
            delay(interval)
        }
    }
}

/**
 * Launches a coroutine that reports failures of [block] to [onError] instead of the scope.
 *
 * Every failure except cancellation is passed to [onError] and the returned
 * [Job] completes normally. A failure thrown by [onError] itself propagates to
 * the scope.
 *
 * Cancellation: a [CancellationException] is rethrown, never passed to
 * [onError], so cancelling the returned [Job] or the scope behaves like a
 * plain [launch].
 *
 * @param onError receives every non-cancellation failure of [block].
 */
public fun CoroutineScope.launchCatching(
    onError: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
    try {
        block()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        onError(cause)
    }
}

/**
 * Launches a coroutine that runs [block] after [delay].
 *
 * Cancelling the returned [Job] during the delay skips [block] entirely.
 */
public fun CoroutineScope.launchDelayed(
    delay: Duration,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
    delay(delay)
    block()
}
