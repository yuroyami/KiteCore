/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class IoDispatcherTest {

    @Test
    fun io_dispatcher_runs_work() = runTest {
        val result = withContext(ioDispatcher) { 6 * 7 }
        assertEquals(42, result)
    }
}
