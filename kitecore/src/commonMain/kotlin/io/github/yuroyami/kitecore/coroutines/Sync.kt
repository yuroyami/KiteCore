/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.concurrent.Volatile
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [block] while holding this mutex only if the mutex is free, returning `null` otherwise.
 *
 * The lock attempt uses [Mutex.tryLock], so this function never waits and
 * `null` means the mutex was held by someone else. The mutex is released even
 * when [block] throws.
 *
 * Cancellation: this function does not suspend and performs no cancellation
 * checks; a non-suspending [block] runs to completion once started.
 */
public inline fun <T> Mutex.tryWithLock(block: () -> T): T? {
    if (!tryLock()) return null
    try {
        return block()
    } finally {
        unlock()
    }
}

/**
 * Runs [block] while holding this mutex, waiting at most [timeout] for the lock, returning `null` on timeout.
 *
 * A non-positive [timeout] degrades to a single [Mutex.tryLock] attempt. Once
 * the lock is acquired, [block] runs without any further deadline and the
 * mutex is released even when [block] throws.
 *
 * Cancellation: waiting for the lock is cancellable and rethrows without
 * leaving the mutex locked; cancellation inside [block] releases the mutex and
 * rethrows.
 *
 * @param timeout how long to wait for the lock.
 */
public suspend fun <T> Mutex.withLockOrNull(timeout: Duration, block: suspend () -> T): T? {
    val acquired = if (timeout <= Duration.ZERO) {
        tryLock()
    } else {
        withTimeoutOrNull(timeout) { lock() } != null
    }
    if (!acquired) return null
    try {
        return block()
    } finally {
        unlock()
    }
}

/**
 * Runs [block] while holding a permit only if one is available, returning `null` otherwise.
 *
 * The permit attempt uses [Semaphore.tryAcquire], so this function never waits
 * and `null` means no permit was free. The permit is released even when
 * [block] throws.
 *
 * Cancellation: this function does not suspend and performs no cancellation
 * checks; a non-suspending [block] runs to completion once started.
 */
public inline fun <T> Semaphore.tryWithPermit(block: () -> T): T? {
    if (!tryAcquire()) return null
    try {
        return block()
    } finally {
        release()
    }
}

/**
 * Runs [block] while holding a permit, waiting at most [timeout] for one, returning `null` on timeout.
 *
 * A non-positive [timeout] degrades to a single [Semaphore.tryAcquire]
 * attempt. Once a permit is acquired, [block] runs without any further
 * deadline and the permit is released even when [block] throws.
 *
 * Cancellation: waiting for a permit is cancellable and rethrows without
 * consuming a permit; cancellation inside [block] releases the permit and
 * rethrows.
 *
 * @param timeout how long to wait for a permit.
 */
public suspend fun <T> Semaphore.withPermitOrNull(timeout: Duration, block: suspend () -> T): T? {
    val acquired = if (timeout <= Duration.ZERO) {
        tryAcquire()
    } else {
        withTimeoutOrNull(timeout) { acquire() } != null
    }
    if (!acquired) return null
    try {
        return block()
    } finally {
        release()
    }
}

/**
 * A map of independent mutexes keyed by [K], with mutual exclusion per key.
 *
 * Two callers using different keys never block each other; two callers using
 * equal keys serialize. Keys need stable [equals] and [hashCode]. Entries are
 * created on demand and removed as soon as the last interested caller leaves,
 * so unused keys hold no memory.
 *
 * Thread safety: this class is safe for concurrent use from multiple
 * coroutines and threads; all internal state is guarded by a [Mutex].
 */
public class KeyedMutex<K> {

    private class Entry {
        val mutex = Mutex()
        var refCount = 0
    }

    private val mapLock = Mutex()
    private val entries = HashMap<K, Entry>()

    private suspend fun checkOut(key: K): Entry = mapLock.withLock {
        entries.getOrPut(key) { Entry() }.also { it.refCount++ }
    }

    private suspend fun checkIn(key: K, entry: Entry) {
        withContext(NonCancellable) {
            mapLock.withLock {
                entry.refCount--
                if (entry.refCount == 0) entries.remove(key)
            }
        }
    }

    /**
     * Runs [block] while holding the mutex for [key].
     *
     * Callers with equal keys run [block] one at a time; callers with other
     * keys are unaffected.
     *
     * Cancellation: waiting for the key's mutex is cancellable and rethrows;
     * cancellation inside [block] releases the mutex and rethrows. Entry
     * cleanup runs in a [NonCancellable] context, so a cancelled caller never
     * leaks its entry.
     */
    public suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        val entry = checkOut(key)
        try {
            return entry.mutex.withLock { block() }
        } finally {
            checkIn(key, entry)
        }
    }

    /**
     * Runs [block] while holding the mutex for [key], waiting at most [timeout], returning `null` on timeout.
     *
     * A non-positive [timeout] degrades to a single non-waiting lock attempt.
     * Once the lock is acquired, [block] runs without any further deadline.
     *
     * Cancellation: waiting for the key's mutex is cancellable and rethrows;
     * cancellation inside [block] releases the mutex and rethrows. Entry
     * cleanup runs in a [NonCancellable] context, so a cancelled caller never
     * leaks its entry.
     *
     * @param timeout how long to wait for the key's mutex.
     */
    public suspend fun <T> withLockOrNull(key: K, timeout: Duration, block: suspend () -> T): T? {
        val entry = checkOut(key)
        try {
            return entry.mutex.withLockOrNull(timeout, block)
        } finally {
            checkIn(key, entry)
        }
    }

    /**
     * Returns `true` while some caller holds the mutex for [key].
     *
     * The answer is a snapshot; the lock state may change immediately after
     * this function returns.
     *
     * Cancellation: waiting for the internal map lock is cancellable and
     * rethrows.
     */
    public suspend fun isLocked(key: K): Boolean = mapLock.withLock {
        entries[key]?.mutex?.isLocked == true
    }

    /**
     * Returns the number of keys currently tracked by this instance.
     *
     * A key is tracked only while at least one caller holds or waits for its
     * mutex, so a quiescent instance reports 0.
     *
     * Cancellation: waiting for the internal map lock is cancellable and
     * rethrows.
     */
    public suspend fun activeKeyCount(): Int = mapLock.withLock { entries.size }
}

/**
 * Deduplicates concurrent work by key: callers with equal keys share one in-flight execution.
 *
 * The first caller for a key runs its block; every caller that arrives while
 * that execution is in flight waits for it and receives the same result or
 * failure. When the execution finishes, the key is released and the next
 * caller starts a fresh one, so completed keys hold no memory.
 *
 * Thread safety: this class is safe for concurrent use from multiple
 * coroutines and threads; all internal state is guarded by a [Mutex].
 */
public class SingleFlight<K, T> {

    private val mapLock = Mutex()
    private val flights = HashMap<K, CompletableDeferred<T>>()

    /**
     * Runs [block] for [key], or joins an execution already in flight for an equal key.
     *
     * The caller that starts the execution runs [block] in its own context;
     * joining callers only wait. Every participant receives the same value, or
     * the same failure thrown as-is.
     *
     * Cancellation: cancelling the executing caller cancels the shared
     * execution; joining callers do not inherit that cancellation, they retry
     * with a fresh execution instead. Cancelling a joining caller only stops
     * its wait and rethrows. Flight cleanup runs in a [NonCancellable]
     * context, so a cancelled execution never leaks its key.
     */
    public suspend fun run(key: K, block: suspend () -> T): T {
        while (true) {
            var owner = false
            val flight = mapLock.withLock {
                flights.getOrPut(key) {
                    owner = true
                    CompletableDeferred()
                }
            }
            if (owner) {
                try {
                    val value = block()
                    flight.complete(value)
                    return value
                } catch (cause: Throwable) {
                    flight.completeExceptionally(cause)
                    throw cause
                } finally {
                    withContext(NonCancellable) {
                        mapLock.withLock { flights.remove(key) }
                    }
                }
            }
            try {
                return flight.await()
            } catch (cause: CancellationException) {
                currentCoroutineContext().ensureActive()
                // The shared execution was cancelled before completing; race
                // for a fresh flight on the next loop iteration.
            }
        }
    }

    /**
     * Returns `true` while an execution for [key] is in flight.
     *
     * The answer is a snapshot; the flight may finish immediately after this
     * function returns.
     *
     * Cancellation: waiting for the internal map lock is cancellable and
     * rethrows.
     */
    public suspend fun isInFlight(key: K): Boolean = mapLock.withLock { flights.containsKey(key) }

    /**
     * Returns the number of keys with an execution currently in flight.
     *
     * A quiescent instance reports 0.
     *
     * Cancellation: waiting for the internal map lock is cancellable and
     * rethrows.
     */
    public suspend fun inFlightKeyCount(): Int = mapLock.withLock { flights.size }
}

/**
 * Runs only the last block submitted within a time [window], discarding earlier ones.
 *
 * Each [submit] replaces the pending block and restarts the window; when the
 * window elapses with no newer submission, the pending block runs as a new
 * child coroutine of [scope]. Failures of a block follow the scope's regular
 * exception handling. A debouncer whose scope is cancelled stops permanently
 * and ignores later submissions.
 *
 * Thread safety: this class is safe for concurrent use from multiple
 * coroutines and threads; [submit] and [cancel] funnel through a conflated
 * [Channel] and never block.
 *
 * @param window how long a submission must stay the newest one before it runs.
 * @param scope the scope that hosts the internal worker and every execution.
 * @throws IllegalArgumentException if [window] is negative.
 */
public class Debouncer(
    public val window: Duration,
    private val scope: CoroutineScope,
) {

    private sealed interface Signal {
        class Run(val block: suspend () -> Unit) : Signal
        object Drop : Signal
    }

    private val signals = Channel<Signal>(Channel.CONFLATED)
    private val worker: Job

    init {
        require(window >= Duration.ZERO) { "window must not be negative, was $window" }
        worker = scope.launch {
            while (true) {
                var pending: Signal = signals.receive()
                while (true) {
                    val task = pending as? Signal.Run ?: break
                    val replacement = withTimeoutOrNull(window) { signals.receive() }
                    if (replacement == null) {
                        scope.launch { task.block() }
                        break
                    }
                    pending = replacement
                }
            }
        }
    }

    /**
     * Returns `true` while the debouncer accepts submissions.
     *
     * Becomes `false` permanently once the hosting scope is cancelled.
     *
     * Cancellation: reading this property does not suspend and performs no
     * cancellation checks.
     */
    public val isActive: Boolean get() = worker.isActive

    /**
     * Submits [block], replacing any pending submission and restarting the window.
     *
     * The block runs only if no newer submission and no [cancel] call arrives
     * before the window elapses.
     *
     * Cancellation: this function does not suspend and performs no
     * cancellation checks. If the hosting scope is already cancelled, the
     * submission is dropped.
     */
    public fun submit(block: suspend () -> Unit) {
        signals.trySend(Signal.Run(block))
    }

    /**
     * Discards the pending submission, if any.
     *
     * An execution whose window already elapsed is not affected, and the
     * debouncer keeps accepting new submissions.
     *
     * Cancellation: this function does not suspend and performs no
     * cancellation checks.
     */
    public fun cancel() {
        signals.trySend(Signal.Drop)
    }
}

/**
 * Runs at most one block per time [window], rejecting calls that arrive too soon.
 *
 * A call outside the window runs its block and returns the value; a call
 * inside the window returns `null` without running anything. The window
 * starts when a permitted block begins, and a failed or cancelled block still
 * consumes it. Time is measured with the injected [TimeSource], which makes
 * the class testable with a virtual clock.
 *
 * Thread safety: this class is safe for concurrent use from multiple
 * coroutines and threads. [run] serializes its admission check with a
 * [Mutex]; [reset] and [isThrottled] read or write a single volatile field,
 * so a [reset] racing an in-progress [run] may be superseded by that run's new
 * window.
 *
 * @param window the minimum time between the starts of two permitted runs.
 * @param timeSource the clock used to measure the window.
 * @throws IllegalArgumentException if [window] is negative.
 */
public class Throttler(
    public val window: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

    init {
        require(window >= Duration.ZERO) { "window must not be negative, was $window" }
    }

    private val stateLock = Mutex()

    @Volatile
    private var lastRun: ComparableTimeMark? = null

    /**
     * Returns `true` while a previous run's window is still open.
     *
     * The answer is a snapshot; the window may close immediately after this
     * property returns.
     *
     * Cancellation: reading this property does not suspend and performs no
     * cancellation checks.
     */
    public val isThrottled: Boolean
        get() {
            val last = lastRun ?: return false
            return last.elapsedNow() < window
        }

    /**
     * Runs [block] and returns its value, or returns `null` without running it while the window is open.
     *
     * The window starts when a permitted block begins; a failed or cancelled
     * block still consumes it. The block runs outside the internal lock, so
     * concurrent callers are rejected instead of queued.
     *
     * Cancellation: waiting for the internal lock is cancellable and rethrows;
     * cancellation inside [block] rethrows after the window has started.
     */
    public suspend fun <T> run(block: suspend () -> T): T? {
        stateLock.withLock {
            val last = lastRun
            if (last != null && last.elapsedNow() < window) return null
            lastRun = timeSource.markNow()
        }
        return block()
    }

    /**
     * Closes the current window so the next [run] call is permitted.
     *
     * A reset that races an in-progress [run] may be superseded by that run's
     * new window.
     *
     * Cancellation: this function does not suspend and performs no
     * cancellation checks.
     */
    public fun reset() {
        lastRun = null
    }
}

private object UninitializedValue

/**
 * A value initialized on first [get], with one shared initialization across concurrent callers.
 *
 * The initializer runs in the context of the first caller. A failed
 * initialization is not cached: the failure propagates to that caller and the
 * next [get] runs the initializer again. After a successful initialization the
 * initializer reference is dropped and every later [get] returns without
 * suspending.
 *
 * Thread safety: this class is safe for concurrent use from multiple
 * coroutines and threads; initialization is guarded by a [Mutex] and the
 * stored value by a volatile field.
 */
public class SuspendLazy<T> internal constructor(initializer: suspend () -> T) {

    private val lock = Mutex()
    private var initializer: (suspend () -> T)? = initializer

    @Volatile
    private var stored: Any? = UninitializedValue

    /**
     * Returns `true` once a value has been successfully initialized.
     *
     * Cancellation: reading this property does not suspend and performs no
     * cancellation checks.
     */
    public val isInitialized: Boolean get() = stored !== UninitializedValue

    /**
     * Returns the initialized value, or `null` when no value exists yet, without initializing.
     *
     * For a nullable [T], a stored `null` is indistinguishable from an
     * uninitialized state; use [isInitialized] to tell them apart.
     *
     * Cancellation: this function does not suspend and performs no
     * cancellation checks.
     */
    public fun valueOrNull(): T? {
        val value = stored
        @Suppress("UNCHECKED_CAST")
        return if (value === UninitializedValue) null else value as T
    }

    /**
     * Returns the value, running the initializer on the first call.
     *
     * Concurrent first calls share one initialization: one caller runs the
     * initializer while the others wait for its outcome.
     *
     * Cancellation: cancelling the initializing caller stops the initializer
     * and leaves the value uninitialized; the next caller runs the initializer
     * again. Cancelling a waiting caller only stops its wait and rethrows.
     */
    public suspend fun get(): T {
        val fast = stored
        if (fast !== UninitializedValue) {
            @Suppress("UNCHECKED_CAST")
            return fast as T
        }
        lock.withLock {
            val seen = stored
            if (seen !== UninitializedValue) {
                @Suppress("UNCHECKED_CAST")
                return seen as T
            }
            val initialize = checkNotNull(initializer) { "initializer is absent before initialization" }
            val value = initialize()
            stored = value
            initializer = null
            return value
        }
    }
}

/**
 * Creates a [SuspendLazy] backed by [initializer].
 *
 * The initializer does not run until the first [SuspendLazy.get] call.
 *
 * Cancellation: this function does not suspend and performs no cancellation
 * checks; cancellation semantics of the initialization are documented on
 * [SuspendLazy.get].
 */
public fun <T> suspendLazy(initializer: suspend () -> T): SuspendLazy<T> = SuspendLazy(initializer)
