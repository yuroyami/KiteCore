/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Emits a value, then ignores new values until [window] has passed. For taps and other bursts.
 *
 * The first value passes at once. Values that arrive in the next [window] are dropped.
 * After the window, the next value passes and starts a new window.
 *
 * ### Example
 * ```kotlin
 * buttonClicks.throttleFirst(500.milliseconds).collect { submitOrder() }
 * // Taps at 0 ms, 100 ms and 700 ms submit twice: at 0 ms and at 700 ms.
 * ```
 *
 * ### Compared with `debounce` and `sample`
 * `debounce` waits for a quiet period and then emits the last value. `sample` emits the
 * latest value once per period. `throttleFirst` emits the first value at once.
 *
 * ### Watch out
 * - Dropped values are lost. After the window, the next new value passes, not the
 *   last dropped value.
 * - The window starts when the collector has finished with the emitted value. A slow
 *   collector makes the effective window longer.
 *
 * @param window how long to drop values after each emitted value. Must be positive.
 * @throws IllegalArgumentException if [window] is zero or negative.
 */
public fun <T> Flow<T>.throttleFirst(window: Duration): Flow<T> {
    require(window > Duration.ZERO) { "window must be positive but was $window" }
    val upstream = this
    return flow {
        coroutineScope {
            val gate = MutableStateFlow(true)
            var timer: Job? = null
            upstream.collect { value ->
                if (gate.compareAndSet(expect = true, update = false)) {
                    emit(value)
                    timer = launch {
                        delay(window)
                        gate.value = true
                    }
                }
            }
            timer?.cancel()
        }
    }
}

/**
 * Like [map][kotlinx.coroutines.flow.map], but runs up to [concurrency] calls of
 * [transform] at the same time. The output keeps the input order.
 *
 * ```kotlin
 * urls.asFlow()
 *     .mapAsync(concurrency = 4) { download(it) }
 *     .collect { save(it) }
 * // Four downloads run at a time. The files arrive in the order of urls.
 * ```
 *
 * ### Compared with `flatMapMerge`
 * `flatMapMerge` emits each result when it is ready, in any order. `mapAsync` keeps
 * the order, so one slow call holds back the results after it.
 *
 * ### How it runs
 * - `mapAsync` reads the next upstream value only when fewer than [concurrency] calls
 *   are waiting. So at most [concurrency] results are in memory.
 * - A failure in [transform] or in the upstream cancels all running calls, and the
 *   flow fails with that exception.
 *
 * @param concurrency the largest number of calls that run at the same time. At least 1.
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public fun <T, R> Flow<T>.mapAsync(concurrency: Int, transform: suspend (T) -> R): Flow<R> {
    require(concurrency >= 1) { "concurrency must be at least 1 but was $concurrency" }
    val upstream = this
    return flow {
        coroutineScope {
            val semaphore = Semaphore(concurrency)
            val results = Channel<Deferred<R>>(capacity = concurrency)
            launch {
                try {
                    upstream.collect { value ->
                        semaphore.acquire()
                        results.send(async { transform(value) })
                    }
                    results.close()
                } catch (t: Throwable) {
                    results.close(t)
                    if (t is CancellationException) throw t
                }
            }
            while (true) {
                val next = results.receiveCatching()
                if (next.isClosed) {
                    next.exceptionOrNull()?.let { throw it }
                    break
                }
                emit(next.getOrThrow().await())
                semaphore.release()
            }
        }
    }
}
