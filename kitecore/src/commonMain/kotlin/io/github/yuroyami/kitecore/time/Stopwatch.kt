/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.time

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A stopwatch that accumulates elapsed time across start and stop cycles.
 *
 * Time is read from [timeSource], which defaults to [TimeSource.Monotonic]. The
 * stopwatch starts out stopped with a zero [elapsed] value. Stopping keeps the
 * accumulated time; use [reset] or [restart] to clear it.
 *
 * This class is not thread-safe. Confine each instance to a single thread or
 * coroutine, or guard it with external synchronization.
 */
public class Stopwatch(
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

    private var accumulated: Duration = Duration.ZERO
    private var startMark: ComparableTimeMark? = null

    /** Whether the stopwatch is currently running. */
    public val isRunning: Boolean
        get() = startMark != null

    /** The total accumulated time, updating live while the stopwatch runs. */
    public val elapsed: Duration
        get() = accumulated + (startMark?.elapsedNow() ?: Duration.ZERO)

    /** Starts the stopwatch, doing nothing when it is already running. */
    public fun start() {
        if (startMark == null) startMark = timeSource.markNow()
    }

    /** Stops the stopwatch and folds the running segment into [elapsed], doing nothing when already stopped. */
    public fun stop() {
        val mark = startMark ?: return
        accumulated += mark.elapsedNow()
        startMark = null
    }

    /** Stops the stopwatch and clears [elapsed] to [Duration.ZERO]. */
    public fun reset() {
        accumulated = Duration.ZERO
        startMark = null
    }

    /** Clears [elapsed] to [Duration.ZERO] and starts the stopwatch in one call. */
    public fun restart() {
        accumulated = Duration.ZERO
        startMark = timeSource.markNow()
    }

    /**
     * Runs [block] while the stopwatch is running and returns the block's result.
     *
     * The stopwatch is started before the block and stopped afterwards, even when
     * the block throws. A stopwatch that was already running keeps its current
     * segment and is stopped when the block completes.
     */
    public inline fun <T> measure(block: () -> T): T {
        start()
        try {
            return block()
        } finally {
            stop()
        }
    }
}

/**
 * A fixed time budget that starts counting down at construction.
 *
 * Time is read from the given time source, which defaults to
 * [TimeSource.Monotonic]. A non-positive [timeout] produces a deadline that is
 * expired from the start. All state is captured at construction, so reading the
 * properties is safe from any coroutine.
 */
public class Deadline(
    /** The full time budget this deadline was created with. */
    public val timeout: Duration,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

    private val start: ComparableTimeMark = timeSource.markNow()

    /** The time passed since construction, growing without bound past [timeout]. */
    public val elapsed: Duration
        get() = start.elapsedNow()

    /** The time left before expiry, floored at [Duration.ZERO]. */
    public val remaining: Duration
        get() = (timeout - start.elapsedNow()).coerceAtLeast(Duration.ZERO)

    /** Whether the deadline has expired. */
    public val isExpired: Boolean
        get() = start.elapsedNow() >= timeout

    /** The expired fraction of [timeout], from 0.0 to 1.0. */
    public val progress: Double
        get() = if (timeout <= Duration.ZERO) 1.0 else (elapsed / timeout).coerceIn(0.0, 1.0)

    /**
     * Throws an [IllegalStateException] when the deadline has expired.
     *
     * @throws IllegalStateException when [isExpired] is true.
     */
    public fun checkNotExpired() {
        check(!isExpired) { "Deadline of $timeout expired" }
    }

    /**
     * Runs [block] bounded by the [remaining] time and returns null on expiry.
     *
     * The block runs under [withTimeoutOrNull], so an already expired deadline
     * returns null without running the block at all.
     */
    public suspend fun <T> orNull(block: suspend () -> T): T? =
        withTimeoutOrNull(remaining) { block() }

    /**
     * Suspends for the [remaining] time in a single delay.
     *
     * The wait length is read once when the call starts, and an expired deadline
     * returns immediately.
     */
    public suspend fun await() {
        delay(remaining)
    }
}
