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
 * Makes callers that ask for the same key at the same time share one run of the work.
 *
 * A flight is one run of the block for one key. The first caller for a key starts a
 * flight. Callers that arrive while it runs join it: they wait and get the same result
 * or the same failure. The name comes from Go's `singleflight` package.
 *
 * ### Example
 * ```kotlin
 * val tokenFlights = SingleFlight<String, Token>()
 *
 * suspend fun token(): Token =
 *     tokenFlights.run("session") { api.refreshToken() }
 *
 * // Three screens call token() at the same time. refreshToken()
 * // runs once, and all three get the same Token.
 * ```
 *
 * ### Compared with a cache
 * `SingleFlight` keeps nothing after a flight ends. The next call for the key starts
 * a new flight. To keep a value, put a cache in front of it, or use [suspendLazy]
 * for a single value.
 *
 * ### Threading
 * All members are safe to call from any coroutine and any thread.
 *
 * @param K the key type. Keys match by `equals`.
 * @param T the result type of the work.
 */
public class SingleFlight<K, T> {

    private class Flight<T>(val owner: Job?) {
        val outcome = CompletableDeferred<Result<T>>()
    }

    private val mapLock = Mutex()
    private val flights = HashMap<K, Flight<T>>()

    /**
     * Runs [block] for [key], or joins the flight that already runs for an equal key.
     *
     * The caller that starts the flight runs [block] in its own coroutine context.
     * A joining caller only waits. Every caller gets the same value, or the same
     * exception, thrown as it is. A timeout of a `withTimeout` inside [block] is such
     * a shared exception.
     *
     * **Cancellation.** When the caller that runs [block] is cancelled, the flight
     * stops. The joining callers do not get that cancellation: they start a new flight
     * instead. When a joining caller is cancelled, only its own wait stops. A cancelled
     * flight always frees its key.
     *
     * @throws IllegalStateException if [block], or a coroutine started in it, calls
     *   [run] with an equal key. That call would wait for itself forever.
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

    /** Returns `true` while a flight runs for [key]. The flight can end right after this returns. */
    public suspend fun isInFlight(key: K): Boolean = mapLock.withLock { flights.containsKey(key) }

    /** Returns the number of keys with a running flight, for example for logs or tests. */
    public suspend fun inFlightKeyCount(): Int = mapLock.withLock { flights.size }
}

private object UninitializedValue

/**
 * A lazy value whose initializer can suspend. Create one with [suspendLazy].
 *
 * The first [get] runs the initializer. Callers that call [get] at the same time
 * wait for that one run. After a success, [get] returns the value without suspending.
 *
 * ### Compared with `lazy`
 * - The initializer is a suspend function, and [get] is a suspend function.
 * - A failure is not stored. The caller gets the exception, and the next [get] runs
 *   the initializer again.
 *
 * ### Threading
 * All members are safe to call from any coroutine and any thread. [isInitialized]
 * and [valueOrNull] never suspend.
 */
public class SuspendLazy<T> internal constructor(initializer: suspend () -> T) {

    private val lock = Mutex()
    private var initializer: (suspend () -> T)? = initializer

    @Volatile
    private var stored: Any? = UninitializedValue

    @Volatile
    private var initializingJob: Job? = null

    /** `true` after the initializer succeeded. */
    public val isInitialized: Boolean get() = stored !== UninitializedValue

    /**
     * Returns the value if the initializer already succeeded, else `null`. It never starts
     * the initializer.
     *
     * When [T] is nullable, a stored `null` looks the same as "not initialized".
     * Check [isInitialized] to tell them apart.
     */
    public fun valueOrNull(): T? {
        val value = stored
        @Suppress("UNCHECKED_CAST")
        return if (value === UninitializedValue) null else value as T
    }

    /**
     * Returns the value. The first call runs the initializer, in the coroutine context of
     * that caller.
     *
     * **Cancellation.** When the caller that runs the initializer is cancelled, the
     * initializer stops and the value stays uninitialized. The next [get] runs it again.
     * When a waiting caller is cancelled, only its own wait stops.
     *
     * @throws IllegalStateException if the initializer, or a coroutine started in it,
     *   calls [get] on this instance. That call would wait for itself forever.
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
 * Like [lazy], but the [initializer] can suspend. Read the value with [SuspendLazy.get].
 *
 * ### Example
 * ```kotlin
 * val database = suspendLazy { openDatabase("app.db") }
 *
 * suspend fun records(): List<Record> = database.get().queryAll()
 * // The first get() opens the database.
 * // Every later get() returns it at once.
 * ```
 *
 * ### Watch out
 * - A failure is not stored. The next [SuspendLazy.get] runs [initializer] again.
 * - [initializer] must not call [SuspendLazy.get] on the same instance. That call
 *   throws [IllegalStateException], because it would wait for itself forever.
 *
 * @param initializer the code that makes the value. It runs again after each failure,
 *   and never again after the first success.
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
