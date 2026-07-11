/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(DelicateCoroutinesApi::class)

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext

/**
 * `Dispatchers.IO` is JVM/Android-only — it is `internal` on Kotlin/Native. To
 * honour the "dispatcher for blocking work" contract on Apple targets, back it
 * with a dedicated fixed thread pool so blocking syscalls don't starve
 * `Dispatchers.Default` (the CPU pool). Process-lifetime singleton, like
 * `Dispatchers.IO` itself — intentionally never closed.
 */
private val appleIo: CoroutineDispatcher by lazy {
    newFixedThreadPoolContext(nThreads = 64, name = "KiteCore-IO")
}

actual fun ioDispatcher(): CoroutineDispatcher = appleIo

actual fun installIoDispatcher(dispatcher: CoroutineDispatcher) {
    // No-op: Apple targets get a real dedicated IO thread pool above.
}
