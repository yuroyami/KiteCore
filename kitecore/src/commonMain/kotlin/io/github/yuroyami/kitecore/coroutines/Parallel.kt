/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Like [map], but runs [transform] for many elements at the same time. The results keep
 * the input order.
 *
 * ### Example
 * ```kotlin
 * val profiles = userIds.parallelMap(concurrency = 8) { id ->
 *     api.fetchProfile(id)
 * }
 * // profiles[i] belongs to userIds[i], whatever order the calls end in.
 * ```
 *
 * ### How it runs
 * - At most [concurrency] calls run at the same time. A large input does not start
 *   one coroutine per element: a fixed group of [concurrency] coroutines takes the
 *   elements in order.
 * - The first failure cancels the other calls, and `parallelMap` throws it.
 * - `parallelMap` copies the elements before it starts. A later change to the source
 *   collection does not change the result.
 *
 * ### Cancellation
 * When the caller is cancelled, every running call is cancelled before the
 * cancellation is thrown.
 *
 * ### Watch out
 * - The default [concurrency] has no limit. For network or disk work, pass a limit.
 * - The calls run on the dispatcher of the caller. For blocking calls, wrap the
 *   work in `withContext(ioDispatcher)`.
 *
 * @param concurrency the largest number of calls that run at the same time. At least 1.
 *   The default, [Int.MAX_VALUE], means no limit.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 * @see parallelForEach
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
 * Like [forEach], but runs [action] for many elements at the same time. Returns when all
 * are done.
 *
 * ```kotlin
 * staleEntries.parallelForEach(concurrency = 16) { cache.evict(it) }
 * ```
 *
 * The rules for the limit, failures, cancellation and the copy of the elements are
 * the same as for [parallelMap].
 *
 * @param concurrency the largest number of calls that run at the same time. At least 1.
 *   The default, [Int.MAX_VALUE], means no limit.
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
