/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/*
 * Dispatchers.IO is public on Kotlin/Native since kotlinx-coroutines 1.7. It is
 * elastic up to 64 threads, parks when idle, and shares its budget with
 * limitedParallelism. On Native it is an extension property in the
 * kotlinx.coroutines package, which requires the explicit `import kotlinx.coroutines.IO`.
 */

public actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

public actual fun installIoDispatcher(dispatcher: CoroutineDispatcher) {
    // No-op: Apple targets already have a real IO thread pool.
}
