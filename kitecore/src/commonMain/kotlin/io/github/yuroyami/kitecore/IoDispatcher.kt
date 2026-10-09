/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatcher for blocking calls (file reads, sockets, JDBC) from common code.
 *
 * `Dispatchers.IO` exists only on some targets. Use `ioDispatcher` in `commonMain`
 * wherever you would write `Dispatchers.IO` on the JVM.
 *
 * ### Example
 * ```kotlin
 * val bytes = withContext(ioDispatcher) { readFileBlocking(path) }
 * ```
 *
 * ### Platform differences
 * | Target | `ioDispatcher` is |
 * | --- | --- |
 * | JVM, Android, iOS, macOS | `Dispatchers.IO`, a thread pool that grows on demand |
 * | JS, Wasm | the dispatcher from [installIoDispatcher], else [Dispatchers.Default] |
 *
 * ### Watch out
 * - Browsers and Node have no thread pool. Without [installIoDispatcher], JS and Wasm
 *   fall back to [Dispatchers.Default], which is the main thread. The first read then
 *   logs one console warning.
 * - Every read returns the currently installed dispatcher. A value that you stored
 *   before [installIoDispatcher] keeps the old dispatcher.
 *
 * @see installIoDispatcher
 */
public expect val ioDispatcher: CoroutineDispatcher

/**
 * Sets the dispatcher that [ioDispatcher] returns on JS and Wasm.
 *
 * KiteCore does not ship a dispatcher for the web. Pass your own, for example one
 * that runs tasks in a Web Worker. Call it once at startup, before any code reads
 * [ioDispatcher]. A later call replaces the earlier dispatcher.
 *
 * ```kotlin
 * fun main() {
 *     // workerDispatcher is your own dispatcher, for example on a Web Worker.
 *     installIoDispatcher(workerDispatcher)
 *     startApp()
 * }
 * ```
 *
 * On JVM, Android, iOS and macOS the call does nothing, so common code can call it
 * on every target.
 *
 * @param dispatcher the dispatcher that [ioDispatcher] returns on JS and Wasm from now on.
 */
public expect fun installIoDispatcher(dispatcher: CoroutineDispatcher)
