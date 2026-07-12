/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The [CoroutineDispatcher] for blocking and IO-bound work on the current target.
 *
 * Platform behavior:
 * - JVM, Android, Apple (iOS, macOS): the real [Dispatchers.IO], an elastic thread pool.
 * - Web (js, wasmJs): no OS thread pool exists. Resolves to the dispatcher set by
 *   [installIoDispatcher] if one was installed, otherwise [Dispatchers.Default] (the
 *   single-threaded JS event loop) with a one-time console warning. To move heavy work
 *   off the main thread, use `WebWorker` or `runInWorker` (available on js and wasmJs),
 *   or generate a worker with kmp-ssot's `web { generateIoWorker = true }`, then pass the
 *   result to [installIoDispatcher].
 *
 * @see installIoDispatcher
 *
 * ```kotlin
 * withContext(ioDispatcher) {
 *     // blocking work
 * }
 * ```
 */
public expect val ioDispatcher: CoroutineDispatcher

/**
 * Sets the [CoroutineDispatcher] that [ioDispatcher] resolves to on the web targets (js, wasmJs).
 *
 * Call once at startup, before the first [ioDispatcher] read. A later call replaces the
 * previously installed dispatcher. On JVM, Android, and Apple this is a no-op, because
 * [ioDispatcher] is already backed by a real thread pool.
 *
 * @param dispatcher the dispatcher to back [ioDispatcher] on web targets.
 * @see ioDispatcher
 */
public expect fun installIoDispatcher(dispatcher: CoroutineDispatcher)
