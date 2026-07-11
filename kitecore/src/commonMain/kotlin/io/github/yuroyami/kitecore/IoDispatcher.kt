/*
 * Copyright 2026 yuroyami
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A dispatcher for blocking / IO-bound work, unified across every KMP target.
 *
 * - **JVM / Android / Apple (iOS, macOS):** the real [Dispatchers.IO]. It exists
 *   on Kotlin/Native since coroutines 1.7 — the "no IO on native" belief is a
 *   closed gap; this exposes one name for all three.
 * - **Web (js / wasmJs):** there is no OS thread pool to block. Returns the
 *   dispatcher installed via [installIoDispatcher] if present, otherwise falls
 *   back to [Dispatchers.Default] (the single JS event loop) and warns once. Use
 *   [KiteWorker] (js) or kmp-ssot's `web { generateIoWorker = true }` to push
 *   heavy work off the main thread, then [installIoDispatcher] the result.
 *
 * Usage: `withContext(ioDispatcher()) { /* blocking work */ }`.
 */
expect fun ioDispatcher(): CoroutineDispatcher

/**
 * Install the dispatcher backing [ioDispatcher] on the web targets (js / wasmJs).
 * No-op on JVM / Android / Apple, where [ioDispatcher] is already a real thread
 * pool. Call once at startup.
 */
expect fun installIoDispatcher(dispatcher: CoroutineDispatcher)

/** `Dispatchers.KiteIO` — sugar for [ioDispatcher]. */
val Dispatchers.KiteIO: CoroutineDispatcher
    get() = ioDispatcher()
