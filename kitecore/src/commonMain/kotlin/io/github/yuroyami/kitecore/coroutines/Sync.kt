/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    private class Flight<T>(val owner: Job?) {
        val outcome = CompletableDeferred<Result<T>>()
    }

    private val mapLock = Mutex()
    private val flights = HashMap<K, Flight<T>>()

    /**
     * Runs [block] for [key], or joins an execution already in flight for an equal key.
     *
     * The caller that starts the execution runs [block] in its own context;
     * joining callers only wait. Every participant receives the same value, or
     * the same failure thrown as-is. A `CancellationException` that [block]
     * throws while its caller is still active, such as the timeout of a
     * `withTimeout` inside [block], is such a shared failure.
     *
     * Cancellation: cancelling the executing caller cancels the shared
     * execution; joining callers do not inherit that cancellation, they retry
     * with a fresh execution instead. Cancelling a joining caller only stops
     * its wait and rethrows. Flight cleanup runs in a [NonCancellable]
     * context, so a cancelled execution never leaks its key.
     *
     * @throws IllegalStateException if [block], or a coroutine started inside
     *   it, calls [run] again with an equal key. That call would wait for its
     *   own execution forever.
     */
    public suspend fun run(key: K, block: suspend () -> T): T {
        val caller = currentCoroutineContext()[Job]
        while (true) {
            var owner = false
            val flight = mapLock.withLock {
                flights.getOrPut(key) {
                    owner = true
                    Flight(caller)
                }
            }
            if (owner) return execute(key, flight, block)
            val flightOwner = flight.owner
            check(caller == null || flightOwner == null || !caller.isSameOrDescendantOf(flightOwner)) {
                "SingleFlight.run was called for key $key from inside its own block, so it would wait for itself forever."
            }
            val outcome = try {
                flight.outcome.await()
            } catch (cause: CancellationException) {
                currentCoroutineContext().ensureActive()
                // The executing caller was cancelled; race for a fresh execution.
                continue
            }
            return outcome.getOrThrow()
        }
    }

    private suspend fun execute(key: K, flight: Flight<T>, block: suspend () -> T): T {
        try {
            val outcome = try {
                Result.success(block())
            } catch (cause: Throwable) {
                Result.failure(cause)
            }
            // Release the key before resolving the flight, so a joiner that retries starts a fresh one.
            withContext(NonCancellable) {
                mapLock.withLock { flights.remove(key) }
            }
            // A failure of a cancelled executor is its cancellation, which joiners must not share.
            if (outcome.isFailure) currentCoroutineContext().ensureActive()
            flight.outcome.complete(outcome)
            return outcome.getOrThrow()
        } finally {
            // No effect after complete; otherwise joiners retry instead of waiting forever.
            flight.outcome.cancel()
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

    @Volatile
    private var initializingJob: Job? = null

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
     *
     * @throws IllegalStateException if the initializer, or a coroutine started
     *   inside it, calls [get] on this instance. That call would wait for its
     *   own initialization forever.
     */
    public suspend fun get(): T {
        val fast = stored
        if (fast !== UninitializedValue) {
            @Suppress("UNCHECKED_CAST")
            return fast as T
        }
        val caller = currentCoroutineContext()[Job]
        val initializing = initializingJob
        check(caller == null || initializing == null || !caller.isSameOrDescendantOf(initializing)) {
            "SuspendLazy.get() was called from inside its own initializer, so it would wait for itself forever."
        }
        lock.withLock {
            val seen = stored
            if (seen !== UninitializedValue) {
                @Suppress("UNCHECKED_CAST")
                return seen as T
            }
            val initialize = checkNotNull(initializer) { "initializer is absent before initialization" }
            initializingJob = caller
            try {
                val value = initialize()
                stored = value
                initializer = null
                return value
            } finally {
                initializingJob = null
            }
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

// Job.parent is experimental; it is only read here, to detect a call that would wait for itself.
@OptIn(ExperimentalCoroutinesApi::class)
private fun Job.isSameOrDescendantOf(ancestor: Job): Boolean {
    var job: Job? = this
    while (job != null) {
        if (job === ancestor) return true
        job = job.parent
    }
    return false
}
