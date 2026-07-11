/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Returns the [CoroutineDispatcher] for blocking and IO-bound work on the current target.
 *
 * Platform behavior:
 * - JVM, Android, Apple (iOS, macOS): the real [Dispatchers.IO], an elastic thread pool.
 * - Web (js, wasmJs): no OS thread pool exists. Returns the dispatcher set by
 *   [installIoDispatcher] if one was installed, otherwise [Dispatchers.Default] (the
 *   single-threaded JS event loop) with a one-time console warning. To move heavy work
 *   off the main thread, use `KiteWorker` or `kiteOffload` (available on js and wasmJs),
 *   or generate a worker with kmp-ssot's `web { generateIoWorker = true }`, then pass the
 *   result to [installIoDispatcher].
 *
 * @see installIoDispatcher
 *
 * ```kotlin
 * withContext(ioDispatcher()) {
 *     // blocking work
 * }
 * ```
 */
public expect fun ioDispatcher(): CoroutineDispatcher

/**
 * Sets the [CoroutineDispatcher] that [ioDispatcher] returns on the web targets (js, wasmJs).
 *
 * Call once at startup, before the first [ioDispatcher] call. A later call replaces the
 * previously installed dispatcher. On JVM, Android, and Apple this is a no-op, because
 * [ioDispatcher] already returns a real thread pool.
 *
 * @param dispatcher the dispatcher to back [ioDispatcher] on web targets.
 * @see ioDispatcher
 */
public expect fun installIoDispatcher(dispatcher: CoroutineDispatcher)

/** Shorthand for [ioDispatcher]. */
public val Dispatchers.KiteIO: CoroutineDispatcher
    get() = ioDispatcher()
