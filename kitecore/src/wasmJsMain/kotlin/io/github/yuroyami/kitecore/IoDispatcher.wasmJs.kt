/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.yuroyami.kitecore

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private var installed: CoroutineDispatcher? = null
private var warned = false

public actual fun installIoDispatcher(dispatcher: CoroutineDispatcher) {
    installed = dispatcher
}

public actual fun ioDispatcher(): CoroutineDispatcher {
    installed?.let { return it }
    if (!warned) {
        warned = true
        wasmConsoleWarn(
            "[KiteCore] ioDispatcher(): no Web Worker dispatcher installed; falling back to " +
                "Dispatchers.Default (single-threaded JS event loop). Heavy/blocking work will not " +
                "leave the main thread. Use KiteWorker / kiteOffload, or supply your own Worker-backed " +
                "CoroutineDispatcher via installIoDispatcher(...)."
        )
    }
    return Dispatchers.Default
}

private fun wasmConsoleWarn(msg: String): Unit = js("console.warn(msg)")
