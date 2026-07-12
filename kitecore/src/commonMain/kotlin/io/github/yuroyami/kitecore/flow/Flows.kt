/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlin.math.pow
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Marker for "no value seen yet" in operators that track a previous or latest element. */
private object NoValue

// ---------------------------------------------------------------------------
// Builders
// ---------------------------------------------------------------------------

/**
 * Returns an infinite flow that emits sequential numbers starting at 0, one every [period].
 *
 * The first number is emitted after [initialDelay]. The delay between emissions is measured
 * from the completion of the previous emission, so a slow collector stretches the schedule
 * instead of piling up values. The flow completes only by cancellation.
 *
 * @throws IllegalArgumentException if [period] is not positive or [initialDelay] is negative.
 */
public fun intervalFlow(period: Duration, initialDelay: Duration = Duration.ZERO): Flow<Long> {
    require(period > Duration.ZERO) { "period must be positive but was $period" }
    require(initialDelay >= Duration.ZERO) { "initialDelay must be non-negative but was $initialDelay" }
    return flow {
        delay(initialDelay)
        var index = 0L
        while (true) {
            emit(index++)
            delay(period)
        }
    }
}

/**
 * Returns a flow that counts down from [from] to 0, one number every [interval].
 *
 * [from] is emitted immediately on collection, each subsequent number after [interval],
 * and the flow completes after emitting 0.
 *
 * @throws IllegalArgumentException if [from] is negative or [interval] is not positive.
 */
public fun countdownFlow(from: Int, interval: Duration): Flow<Int> {
    require(from >= 0) { "from must be non-negative but was $from" }
    require(interval > Duration.ZERO) { "interval must be positive but was $interval" }
    return flow {
        for (value in from downTo 0) {
            if (value != from) delay(interval)
            emit(value)
        }
    }
}

/**
 * Returns a flow that emits a single [Unit] after [delay] and then completes.
 *
 * @throws IllegalArgumentException if [delay] is negative.
 */
public fun timerFlow(delay: Duration): Flow<Unit> {
    require(delay >= Duration.ZERO) { "delay must be non-negative but was $delay" }
    return flow {
        delay(delay)
        emit(Unit)
    }
}

/**
 * Returns a cold flow that invokes [block] and emits its single result.
 *
 * [block] runs once per collection, so each collector observes a fresh invocation.
 */
public fun <T> flowFromSuspend(block: suspend () -> T): Flow<T> = flow { emit(block()) }

/**
 * Returns a cold flow that obtains the actual flow from [provider] at collection time.
 *
 * [provider] runs once per collection, so each collector can observe a different flow.
 */
public fun <T> deferFlow(provider: suspend () -> Flow<T>): Flow<T> = flow { emitAll(provider()) }

/**
 * Returns a flow that never emits and never completes.
 *
 * Collection suspends until the collector is cancelled.
 */
public fun neverFlow(): Flow<Nothing> = flow { awaitCancellation() }

// ---------------------------------------------------------------------------
// Rate limiting and batching
// ---------------------------------------------------------------------------

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
 * Collects values into lists bounded by a time [window] and an optional [maxSize].
 *
 * A chunk opens when its first value arrives and is emitted either when [window] has elapsed
 * since the chunk opened or when it reaches [maxSize], whichever happens first. A non-empty
 * chunk in progress is emitted when the upstream completes. Empty lists are never emitted.
 * The upstream is collected without conflation; values wait for the collector when it is slow.
 *
 * @throws IllegalArgumentException if [window] is not positive or [maxSize] is less than 1.
 */
public fun <T> Flow<T>.chunked(window: Duration, maxSize: Int = Int.MAX_VALUE): Flow<List<T>> {
    require(window > Duration.ZERO) { "window must be positive but was $window" }
    require(maxSize >= 1) { "maxSize must be at least 1 but was $maxSize" }
    val upstream = this
    return flow {
        coroutineScope {
            val values = Channel<T>(Channel.RENDEZVOUS)
            launch {
                try {
                    upstream.collect { values.send(it) }
                    values.close()
                } catch (t: Throwable) {
                    values.close(t)
                    if (t is CancellationException) throw t
                }
            }
            var done = false
            while (!done) {
                val first = values.receiveCatching()
                if (first.isClosed) {
                    first.exceptionOrNull()?.let { throw it }
                    break
                }
                val batch = mutableListOf(first.getOrThrow())
                if (batch.size < maxSize) {
                    withTimeoutOrNull(window) {
                        while (batch.size < maxSize) {
                            val next = values.receiveCatching()
                            if (next.isClosed) {
                                next.exceptionOrNull()?.let { throw it }
                                done = true
                                break
                            }
                            batch.add(next.getOrThrow())
                        }
                    }
                }
                emit(batch)
            }
        }
    }
}

/**
 * Emits sliding windows of [size] elements, advancing by [step] elements between windows.
 *
 * A window is emitted only when it is full; a trailing partial window is dropped.
 * When [step] is greater than [size], the elements between windows are skipped.
 *
 * @throws IllegalArgumentException if [size] or [step] is less than 1.
 */
public fun <T> Flow<T>.windowed(size: Int, step: Int = 1): Flow<List<T>> {
    require(size >= 1) { "size must be at least 1 but was $size" }
    require(step >= 1) { "step must be at least 1 but was $step" }
    val upstream = this
    return flow {
        val buffer = ArrayDeque<T>(size)
        var toSkip = 0
        upstream.collect { value ->
            if (toSkip > 0) {
                toSkip--
                return@collect
            }
            buffer.addLast(value)
            if (buffer.size == size) {
                emit(buffer.toList())
                repeat(minOf(step, size)) { buffer.removeFirst() }
                if (step > size) toSkip = step - size
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pairing
// ---------------------------------------------------------------------------

/**
 * Emits each value together with the value that preceded it, `null` for the first value.
 *
 * For upstream `a, b, c` the output is `(null, a), (a, b), (b, c)`.
 */
public fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> {
    val upstream = this
    return flow {
        var previous: T? = null
        upstream.collect { value ->
            emit(previous to value)
            previous = value
        }
    }
}

/**
 * Emits consecutive pairs of upstream values, starting with the second value.
 *
 * For upstream `a, b, c` the output is `(a, b), (b, c)`. Flows with fewer than two
 * values produce nothing.
 */
public fun <T> Flow<T>.pairwise(): Flow<Pair<T, T>> = pairwise { previous, current -> previous to current }

/**
 * Applies [transform] to each pair of consecutive upstream values.
 *
 * For upstream `a, b, c` the output is `transform(a, b), transform(b, c)`. Flows with
 * fewer than two values produce nothing.
 */
public fun <T, R> Flow<T>.pairwise(transform: suspend (previous: T, current: T) -> R): Flow<R> {
    val upstream = this
    return flow {
        var previous: Any? = NoValue
        upstream.collect { value ->
            if (previous !== NoValue) {
                @Suppress("UNCHECKED_CAST")
                emit(transform(previous as T, value))
            }
            previous = value
        }
    }
}

// ---------------------------------------------------------------------------
// Gating and limiting
// ---------------------------------------------------------------------------

/**
 * Mirrors the upstream until [signal] emits its first value, then completes.
 *
 * If [signal] completes without emitting, the upstream is mirrored to completion.
 * A failure in either the upstream or [signal] fails the resulting flow. Values are
 * handed to the collector without buffering, so nothing is delivered after the signal fires.
 */
public fun <T> Flow<T>.takeUntil(signal: Flow<*>): Flow<T> {
    val upstream = this
    return channelFlow {
        val producer = launch {
            upstream.collect { send(it) }
        }
        val watcher = launch {
            if (signal.take(1).count() > 0) producer.cancel()
        }
        producer.join()
        watcher.cancel()
    }.buffer(Channel.RENDEZVOUS)
}

/**
 * Drops upstream values until [signal] emits its first value, then mirrors the upstream.
 *
 * If [signal] completes without emitting, every upstream value is dropped and the resulting
 * flow completes with the upstream. A failure in either flow fails the resulting flow.
 */
public fun <T> Flow<T>.dropUntil(signal: Flow<*>): Flow<T> {
    val upstream = this
    return channelFlow {
        val open = CompletableDeferred<Unit>()
        val watcher = launch {
            if (signal.take(1).count() > 0) open.complete(Unit)
        }
        upstream.collect { value ->
            if (open.isCompleted) send(value)
        }
        watcher.cancel()
    }.buffer(Channel.RENDEZVOUS)
}

/**
 * Mirrors the upstream for [duration], then completes.
 *
 * Shorthand for `takeUntil(timerFlow(duration))`.
 *
 * @throws IllegalArgumentException if [duration] is negative.
 */
public fun <T> Flow<T>.takeFor(duration: Duration): Flow<T> = takeUntil(timerFlow(duration))

/**
 * Drops upstream values for [duration], then mirrors the upstream.
 *
 * Shorthand for `dropUntil(timerFlow(duration))`.
 *
 * @throws IllegalArgumentException if [duration] is negative.
 */
public fun <T> Flow<T>.dropFor(duration: Duration): Flow<T> = dropUntil(timerFlow(duration))

/**
 * Emits values while [predicate] returns `true`, including the first value for which it returns `false`.
 *
 * Unlike `takeWhile`, the value that terminates the flow is emitted before completion.
 * The upstream is cancelled once the predicate fails.
 */
public fun <T> Flow<T>.takeWhileInclusive(predicate: suspend (T) -> Boolean): Flow<T> =
    transformWhile { value ->
        emit(value)
        predicate(value)
    }

// ---------------------------------------------------------------------------
// Concatenation
// ---------------------------------------------------------------------------

/**
 * Emits [value] before the upstream values.
 *
 * The value is emitted once per collection, before the upstream is collected.
 */
public fun <T> Flow<T>.startWith(value: T): Flow<T> = onStart { emit(value) }

/**
 * Emits [values] in order before the upstream values.
 *
 * The values are emitted once per collection, before the upstream is collected.
 */
public fun <T> Flow<T>.startWith(vararg values: T): Flow<T> {
    val prefix = values.toList()
    return onStart { prefix.forEach { emit(it) } }
}

/**
 * Emits the upstream values followed by the values of [other].
 *
 * [other] is collected only after the upstream completes normally; if the upstream fails
 * or the collector is cancelled, [other] is never collected.
 */
public fun <T> Flow<T>.concatWith(other: Flow<T>): Flow<T> = onCompletion { cause ->
    if (cause == null) emitAll(other)
}

/**
 * Emits the upstream values followed by the values of each flow in [others], in order.
 *
 * Each flow is collected only after all preceding flows complete normally; a failure
 * anywhere stops the concatenation.
 */
public fun <T> Flow<T>.concatWith(vararg others: Flow<T>): Flow<T> {
    val suffix = others.toList()
    return onCompletion { cause ->
        if (cause == null) suffix.forEach { emitAll(it) }
    }
}

// ---------------------------------------------------------------------------
// Concurrent mapping
// ---------------------------------------------------------------------------

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

/**
 * Maps values through [transform] with up to [concurrency] invocations in flight, emitting results in completion order.
 *
 * Results are emitted as soon as they are ready, so the output order can differ from the
 * upstream order. The upstream is collected only while permits are available. A failure in
 * [transform] or the upstream cancels all in-flight work and fails the flow.
 *
 * @throws IllegalArgumentException if [concurrency] is less than 1.
 */
public fun <T, R> Flow<T>.mapAsyncUnordered(concurrency: Int, transform: suspend (T) -> R): Flow<R> {
    require(concurrency >= 1) { "concurrency must be at least 1 but was $concurrency" }
    val upstream = this
    return channelFlow {
        val semaphore = Semaphore(concurrency)
        upstream.collect { value ->
            semaphore.acquire()
            launch {
                try {
                    send(transform(value))
                } finally {
                    semaphore.release()
                }
            }
        }
    }.buffer(Channel.RENDEZVOUS)
}

// ---------------------------------------------------------------------------
// Errors and results
// ---------------------------------------------------------------------------

/**
 * Retries a failed upstream up to [retries] times with exponentially growing delays.
 *
 * The delay before retry `n` (0-based) is `initialDelay * factor.pow(n)`, capped at [maxDelay].
 * A failure is retried only while [retryIf] returns `true` for it; [CancellationException]
 * is never retried. Once retries are exhausted the last failure is rethrown to the collector.
 *
 * @throws IllegalArgumentException if [retries] is negative, [initialDelay] is negative,
 *   or [factor] is not positive.
 */
public fun <T> Flow<T>.retryWithBackoff(
    retries: Int,
    initialDelay: Duration,
    factor: Double = 2.0,
    maxDelay: Duration = Duration.INFINITE,
    retryIf: (Throwable) -> Boolean = { true },
): Flow<T> {
    require(retries >= 0) { "retries must be non-negative but was $retries" }
    require(initialDelay >= Duration.ZERO) { "initialDelay must be non-negative but was $initialDelay" }
    require(factor > 0.0) { "factor must be positive but was $factor" }
    return retryWhen { cause, attempt ->
        if (cause is CancellationException || attempt >= retries || !retryIf(cause)) {
            false
        } else {
            delay((initialDelay * factor.pow(attempt.toInt())).coerceAtMost(maxDelay))
            true
        }
    }
}

/**
 * Wraps each upstream value in [Result.success] and a terminal upstream failure in a single [Result.failure].
 *
 * After a failure is emitted the flow completes normally, so collectors never observe an
 * exception from the upstream. Cancellation is not wrapped and propagates as usual.
 */
public fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    map { Result.success(it) }.catch { emit(Result.failure(it)) }

/**
 * Emits the value of every successful [Result] and drops failures.
 */
public fun <T> Flow<Result<T>>.filterSuccess(): Flow<T> = transform { result ->
    if (result.isSuccess) emit(result.getOrThrow())
}

/**
 * Emits the exception of every failed [Result] and drops successes.
 */
public fun <T> Flow<Result<T>>.filterFailure(): Flow<Throwable> = transform { result ->
    val exception = result.exceptionOrNull()
    if (exception != null) emit(exception)
}

// ---------------------------------------------------------------------------
// Indexed and positional operators
// ---------------------------------------------------------------------------

/**
 * Maps each value through [transform] together with its 0-based position in the flow.
 *
 * The index restarts at 0 for every collection.
 */
public fun <T, R> Flow<T>.mapIndexed(transform: suspend (index: Int, value: T) -> R): Flow<R> {
    val upstream = this
    return flow {
        var index = 0
        upstream.collect { value -> emit(transform(index++, value)) }
    }
}

/**
 * Emits only the values for which [predicate] returns `true`, passing each value's 0-based position.
 *
 * The index counts every upstream value, including filtered ones, and restarts at 0 for
 * every collection.
 */
public fun <T> Flow<T>.filterIndexed(predicate: suspend (index: Int, value: T) -> Boolean): Flow<T> {
    val upstream = this
    return flow {
        var index = 0
        upstream.collect { value ->
            if (predicate(index++, value)) emit(value)
        }
    }
}

/**
 * Invokes [action] with each value and its 0-based position, then emits the value unchanged.
 *
 * The index restarts at 0 for every collection.
 */
public fun <T> Flow<T>.onEachIndexed(action: suspend (index: Int, value: T) -> Unit): Flow<T> {
    val upstream = this
    return flow {
        var index = 0
        upstream.collect { value ->
            action(index++, value)
            emit(value)
        }
    }
}

/**
 * Invokes [action] with the first value only, then emits all values unchanged.
 *
 * [action] runs before the first value is emitted downstream and runs again on every
 * new collection.
 */
public fun <T> Flow<T>.onFirst(action: suspend (T) -> Unit): Flow<T> {
    val upstream = this
    return flow {
        var first = true
        upstream.collect { value ->
            if (first) {
                first = false
                action(value)
            }
            emit(value)
        }
    }
}

/**
 * Emits the running number of values collected so far, starting at 1 for the first value.
 *
 * The count restarts for every collection. An empty upstream produces nothing.
 */
public fun <T> Flow<T>.runningCount(): Flow<Int> {
    val upstream = this
    return flow {
        var count = 0
        upstream.collect { emit(++count) }
    }
}

/**
 * Emits [separator] between each pair of consecutive upstream values.
 *
 * For upstream `a, b, c` the output is `a, separator, b, separator, c`. Flows with
 * fewer than two values are emitted unchanged.
 */
public fun <T> Flow<T>.intersperse(separator: T): Flow<T> {
    val upstream = this
    return flow {
        var anyEmitted = false
        upstream.collect { value ->
            if (anyEmitted) emit(separator)
            emit(value)
            anyEmitted = true
        }
    }
}

/**
 * Replaces every upstream value with [Unit].
 *
 * Useful when only the fact that something happened matters, not the value.
 */
public fun <T> Flow<T>.mapToUnit(): Flow<Unit> = map { }

/**
 * Emits every element of every upstream iterable, in order.
 *
 * Empty iterables produce nothing.
 */
public fun <T> Flow<Iterable<T>>.flattenIterable(): Flow<T> = transform { iterable ->
    iterable.forEach { emit(it) }
}

// ---------------------------------------------------------------------------
// Combination
// ---------------------------------------------------------------------------

/**
 * Combines each upstream value with the latest value of [other] through [transform].
 *
 * Upstream values that arrive before [other] has emitted are dropped. Emissions of [other]
 * alone never produce output; only the upstream drives emissions. A failure in either flow
 * fails the resulting flow.
 */
public fun <T, U, R> Flow<T>.withLatestFrom(other: Flow<U>, transform: suspend (T, U) -> R): Flow<R> {
    val upstream = this
    return channelFlow {
        val latest = MutableStateFlow<Any?>(NoValue)
        val sampler = launch {
            other.collect { latest.value = it }
        }
        upstream.collect { value ->
            val current = latest.value
            if (current !== NoValue) {
                @Suppress("UNCHECKED_CAST")
                send(transform(value, current as U))
            }
        }
        sampler.cancel()
    }.buffer(Channel.RENDEZVOUS)
}

/**
 * Pairs each upstream value with the latest value of [other].
 *
 * Upstream values that arrive before [other] has emitted are dropped.
 */
public fun <T, U> Flow<T>.withLatestFrom(other: Flow<U>): Flow<Pair<T, U>> =
    withLatestFrom(other) { value, latest -> value to latest }

// ---------------------------------------------------------------------------
// Timeouts
// ---------------------------------------------------------------------------

/**
 * Fails with [kotlinx.coroutines.TimeoutCancellationException] when the gap between upstream emissions exceeds [timeout].
 *
 * The timeout also applies to the gap between the start of collection and the first emission.
 * Values already emitted are unaffected; on timeout the upstream is cancelled and the
 * exception is thrown to the collector.
 *
 * @throws IllegalArgumentException if [timeout] is not positive.
 */
public fun <T> Flow<T>.timeoutBetweenEmissions(timeout: Duration): Flow<T> {
    require(timeout > Duration.ZERO) { "timeout must be positive but was $timeout" }
    val upstream = this
    return flow {
        coroutineScope {
            val values = Channel<T>(Channel.RENDEZVOUS)
            launch {
                try {
                    upstream.collect { values.send(it) }
                    values.close()
                } catch (t: Throwable) {
                    values.close(t)
                    if (t is CancellationException) throw t
                }
            }
            while (true) {
                val next = withTimeout(timeout) { values.receiveCatching() }
                if (next.isClosed) {
                    next.exceptionOrNull()?.let { throw it }
                    break
                }
                emit(next.getOrThrow())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Terminal operators
// ---------------------------------------------------------------------------

/**
 * Returns the first value of the flow, or [default] if the flow completes without emitting.
 *
 * The upstream is cancelled after the first value. A `null` first value is returned as is
 * when `T` is nullable; [default] is used only for an empty flow.
 */
public suspend fun <T> Flow<T>.firstOrDefault(default: T): T {
    val first = take(1).toList()
    return if (first.isEmpty()) default else first[0]
}

/**
 * Returns the last value of the flow, or [default] if the flow completes without emitting.
 *
 * The flow is collected to completion.
 */
public suspend fun <T> Flow<T>.lastOrDefault(default: T): T =
    fold(default) { _, value -> value }

/**
 * Launches collection of the flow in [scope], invoking [action] for each value, and returns the [Job].
 *
 * Shorthand for `scope.launch { collect(action) }`. Collection stops when the returned job
 * or [scope] is cancelled; an upstream failure fails the job through the scope.
 */
public fun <T> Flow<T>.collectIn(scope: CoroutineScope, action: suspend (T) -> Unit): Job =
    scope.launch { collect(action) }

// ---------------------------------------------------------------------------
// combine arities 6..9
// ---------------------------------------------------------------------------

/**
 * Combines the latest values of six flows through [transform].
 *
 * The resulting flow emits after every input has emitted at least once and re-emits whenever
 * any input produces a new value. Bursts of updates may be conflated; [transform] always
 * observes the latest values.
 */
public fun <T1, T2, T3, T4, T5, T6, R> combine6(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R,
): Flow<R> = combine(
    combine(flow, flow2, flow3, ::Triple),
    combine(flow4, flow5, flow6, ::Triple),
) { (v1, v2, v3), (v4, v5, v6) ->
    transform(v1, v2, v3, v4, v5, v6)
}

/**
 * Combines the latest values of seven flows through [transform].
 *
 * The resulting flow emits after every input has emitted at least once and re-emits whenever
 * any input produces a new value. Bursts of updates may be conflated; [transform] always
 * observes the latest values.
 */
public fun <T1, T2, T3, T4, T5, T6, T7, R> combine7(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R,
): Flow<R> = combine(
    combine(flow, flow2, flow3, ::Triple),
    combine(flow4, flow5, flow6, ::Triple),
    flow7,
) { (v1, v2, v3), (v4, v5, v6), v7 ->
    transform(v1, v2, v3, v4, v5, v6, v7)
}

/**
 * Combines the latest values of eight flows through [transform].
 *
 * The resulting flow emits after every input has emitted at least once and re-emits whenever
 * any input produces a new value. Bursts of updates may be conflated; [transform] always
 * observes the latest values.
 */
public fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combine8(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8) -> R,
): Flow<R> = combine(
    combine(flow, flow2, flow3, ::Triple),
    combine(flow4, flow5, flow6, ::Triple),
    combine(flow7, flow8, ::Pair),
) { (v1, v2, v3), (v4, v5, v6), (v7, v8) ->
    transform(v1, v2, v3, v4, v5, v6, v7, v8)
}

/**
 * Combines the latest values of nine flows through [transform].
 *
 * The resulting flow emits after every input has emitted at least once and re-emits whenever
 * any input produces a new value. Bursts of updates may be conflated; [transform] always
 * observes the latest values.
 */
public fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combine9(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R,
): Flow<R> = combine(
    combine(flow, flow2, flow3, ::Triple),
    combine(flow4, flow5, flow6, ::Triple),
    combine(flow7, flow8, flow9, ::Triple),
) { (v1, v2, v3), (v4, v5, v6), (v7, v8, v9) ->
    transform(v1, v2, v3, v4, v5, v6, v7, v8, v9)
}

/**
 * Emits the most recent value once per [window], dropping intermediate values.
 *
 * The first value emits immediately; while the window is open, newer upstream values
 * replace each other and only the latest one emits when the window elapses.
 */
public fun <T> Flow<T>.throttleLatest(window: Duration): Flow<T> =
    conflate().transform { value ->
        emit(value)
        delay(window)
    }

/**
 * Groups values into lists of [size] elements, emitting the final partial list on completion.
 *
 * @throws IllegalArgumentException when [size] is less than 1.
 */
public fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> {
    require(size >= 1) { "size must be at least 1, was $size" }
    return flow {
        var batch = ArrayList<T>(size)
        collect { value ->
            batch.add(value)
            if (batch.size == size) {
                emit(batch)
                batch = ArrayList(size)
            }
        }
        if (batch.isNotEmpty()) emit(batch)
    }
}
