/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Transforms every element concurrently and returns the results in the original order.
 *
 * At most [concurrency] transforms run at the same time. Below the element
 * count, a fixed pool of [concurrency] worker coroutines takes the elements in
 * order, so a large input does not create one coroutine per element. The first
 * failing transform cancels the remaining work and its failure is rethrown to
 * the caller. The elements are copied before the work starts, so a later change
 * to the source collection does not affect the result.
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
    val elements = toList()
    if (concurrency >= elements.size) {
        return coroutineScope {
            elements.map { element -> async { transform(element) } }.awaitAll()
        }
    }
    val results = arrayOfNulls<Any?>(elements.size)
    forEachIndexConcurrently(elements.size, concurrency) { index ->
        results[index] = transform(elements[index])
    }
    @Suppress("UNCHECKED_CAST")
    return results.asList() as List<R>
}

/**
 * Runs [action] for every element concurrently and returns when all have finished.
 *
 * At most [concurrency] actions run at the same time. Below the element count,
 * a fixed pool of [concurrency] worker coroutines takes the elements in order,
 * so a large input does not create one coroutine per element. The first failing
 * action cancels the remaining work and its failure is rethrown to the caller.
 * The elements are copied before the work starts.
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
    val elements = toList()
    if (concurrency >= elements.size) {
        coroutineScope {
            for (element in elements) launch { action(element) }
        }
        return
    }
    forEachIndexConcurrently(elements.size, concurrency) { index -> action(elements[index]) }
}

/** Runs [action] for each index below [count] on [workers] coroutines that take the indexes in order. */
private suspend fun forEachIndexConcurrently(count: Int, workers: Int, action: suspend (index: Int) -> Unit) {
    coroutineScope {
        val indexes = Channel<Int>()
        launch {
            try {
                for (index in 0 until count) indexes.send(index)
            } finally {
                indexes.close()
            }
        }
        repeat(workers) {
            launch {
                for (index in indexes) action(index)
            }
        }
    }
}
