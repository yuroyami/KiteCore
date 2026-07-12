/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

public actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

public actual fun installIoDispatcher(dispatcher: CoroutineDispatcher) {
    // No-op: Android already has a real IO thread pool.
}
