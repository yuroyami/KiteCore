/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
