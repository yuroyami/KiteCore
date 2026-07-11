/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertSame

class IoDispatcherInstallTest {

    // Single method: the three assertions are order-dependent (install is global
    // one-way state), so they must not be split across test methods.
    @Test
    fun fallback_then_install_then_installed_wins() {
        assertSame(Dispatchers.Default, ioDispatcher())
        installIoDispatcher(Dispatchers.Unconfined)
        assertSame(Dispatchers.Unconfined, ioDispatcher())
    }
}
