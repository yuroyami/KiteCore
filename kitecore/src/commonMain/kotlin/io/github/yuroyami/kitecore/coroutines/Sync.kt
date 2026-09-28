/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.coroutines

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
