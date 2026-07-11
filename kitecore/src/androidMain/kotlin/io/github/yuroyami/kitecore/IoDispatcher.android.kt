/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

actual fun installIoDispatcher(dispatcher: CoroutineDispatcher) {
    // No-op: Android already has a real IO thread pool.
}
