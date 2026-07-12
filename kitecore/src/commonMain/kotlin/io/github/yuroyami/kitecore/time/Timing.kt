/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.time

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A sliding-window rate limiter that grants at most [permits] acquisitions within any window of length [per].
 *
 * Algorithm: the limiter remembers the time mark of each recent acquisition and
 * grants a permit whenever fewer than [permits] marks are younger than [per].
 *
 * [acquire] and [tryAcquire] are coroutine-safe: shared state is guarded by a
 * [Mutex], and a coroutine suspended in [acquire] holds the mutex while it
 * waits, so permits are granted to waiters in arrival order.
 */
public class RateLimiter(
    /** The maximum number of acquisitions granted within one window. */
    public val permits: Int,
    /** The length of the sliding window. */
    public val per: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

    init {
        require(permits > 0) { "permits must be positive, was $permits" }
        require(per > Duration.ZERO) { "per must be positive, was $per" }
    }

    private val mutex = Mutex()
    private val marks = ArrayDeque<ComparableTimeMark>()

    /**
     * A best-effort snapshot of how many permits are free right now.
     *
     * The value can be stale as soon as it is read; use [tryAcquire] to claim a
     * permit.
     */
    public val availablePermits: Int
        get() = (permits - marks.count { it.elapsedNow() < per }).coerceAtLeast(0)

    /** Suspends until a permit is free, then consumes it. */
    public suspend fun acquire() {
        mutex.withLock {
            while (true) {
                prune()
                if (marks.size < permits) {
                    marks.addLast(timeSource.markNow())
                    return
                }
                delay(per - marks.first().elapsedNow())
            }
        }
    }

    /**
     * Consumes a permit when one is free right now and reports whether it did.
     *
     * Returns false when no permit is free or when the limiter is busy, that is
     * while another coroutine is inside [acquire] or [tryAcquire].
     */
    public fun tryAcquire(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            prune()
            if (marks.size < permits) {
                marks.addLast(timeSource.markNow())
                return true
            }
            return false
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Acquires a permit via [acquire], then runs [block] and returns its result.
     *
     * The permit is consumed rather than held: it frees on its own once it
     * leaves the sliding window, regardless of how long [block] runs.
     */
    public suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        return block()
    }

    private fun prune() {
        while (marks.isNotEmpty() && marks.first().elapsedNow() >= per) marks.removeFirst()
    }
}

/**
 * Runs [block] and then delays so the whole call takes at least [minimum].
 *
 * When the block finishes early, the call delays for the difference, which keeps
 * short operations behind a loading indicator from flickering. When the block
 * takes [minimum] or longer no extra delay is added, and when the block throws,
 * the exception propagates without any padding delay.
 *
 * @param minimum the smallest total duration the call should take.
 * @param timeSource the time source used to measure the block, [TimeSource.Monotonic] by default.
 */
public suspend fun <T> withMinimumDuration(
    minimum: Duration,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    block: suspend () -> T,
): T {
    val start = timeSource.markNow()
    val result = block()
    delay(minimum - start.elapsedNow())
    return result
}

/**
 * Polls [condition] until it returns true or [timeout] elapses, and reports whether it became true.
 *
 * The condition is checked once immediately, then again after every [interval].
 * A non-positive [timeout] still performs the immediate check.
 *
 * @throws IllegalArgumentException when [interval] is not positive.
 */
public suspend fun pollUntil(
    interval: Duration,
    timeout: Duration,
    condition: suspend () -> Boolean,
): Boolean {
    require(interval > Duration.ZERO) { "interval must be positive, was $interval" }
    if (condition()) return true
    return withTimeoutOrNull(timeout) {
        do {
            delay(interval)
        } while (!condition())
        true
    } ?: false
}

/**
 * Returns an infinite sequence of exponentially growing backoff delays.
 *
 * The first element is [initialDelay] and every following element is the
 * previous one multiplied by [factor], with every element capped at [maxDelay].
 * The sequence can be iterated any number of times.
 *
 * @throws IllegalArgumentException when [initialDelay] or [maxDelay] is negative or [factor] is below 1.0.
 */
public fun backoffSequence(
    initialDelay: Duration,
    factor: Double = 2.0,
    maxDelay: Duration = Duration.INFINITE,
): Sequence<Duration> {
    require(!initialDelay.isNegative()) { "initialDelay must not be negative, was $initialDelay" }
    require(factor >= 1.0) { "factor must be at least 1.0, was $factor" }
    require(!maxDelay.isNegative()) { "maxDelay must not be negative, was $maxDelay" }
    return sequence {
        var current = initialDelay.coerceAtMost(maxDelay)
        while (true) {
            yield(current)
            current = (current * factor).coerceAtMost(maxDelay)
        }
    }
}
