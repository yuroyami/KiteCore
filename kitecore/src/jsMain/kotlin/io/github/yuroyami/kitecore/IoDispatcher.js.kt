/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private var installed: CoroutineDispatcher? = null
private var warned = false

public actual fun installIoDispatcher(dispatcher: CoroutineDispatcher) {
    installed = dispatcher
}

public actual val ioDispatcher: CoroutineDispatcher
    get() {
        installed?.let { return it }
        if (!warned) {
            warned = true
            jsConsoleWarn(
                "[KiteCore] ioDispatcher: no Web Worker dispatcher installed; falling back to " +
                    "Dispatchers.Default (single-threaded JS event loop). Heavy/blocking work will not " +
                    "leave the main thread. Use WebWorker / runInWorker, or kmpSsot { web { generateIoWorker = true } }."
            )
        }
        return Dispatchers.Default
    }

private fun jsConsoleWarn(msg: String) {
    js("console.warn(msg)")
}
