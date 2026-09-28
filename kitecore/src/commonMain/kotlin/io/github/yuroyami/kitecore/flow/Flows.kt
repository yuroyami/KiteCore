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
 * Emits the first value, then drops every value that arrives within [window] of the last emission.
 *
 * The window opens again once [window] has elapsed after an emitted value; the next value to
 * arrive is emitted and starts a new window. Dropped values are lost, not conflated.
 * Cancellation of the collector cancels the upstream and the internal timer.
 *
 * @throws IllegalArgumentException if [window] is not positive.
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
 * Maps values through [transform] with up to [concurrency] invocations in flight, preserving upstream order.
 *
 * A result is emitted only after the results of all earlier values have been emitted, so one
 * slow transform delays later results. The upstream is collected only while permits are
 * available, which bounds memory at [concurrency] pending results. A failure in [transform]
 * or the upstream cancels all in-flight work and fails the flow.
 *
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
