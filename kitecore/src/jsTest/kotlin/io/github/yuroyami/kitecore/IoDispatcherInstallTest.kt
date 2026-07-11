/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertSame

class IoDispatcherInstallTest {

    // Order-dependent: installIoDispatcher sets global one-way state, so the
    // fallback check and the post-install check must stay in one method.
    @Test
    fun fallback_then_install_then_installed_wins() {
        assertSame(Dispatchers.Default, ioDispatcher())
        installIoDispatcher(Dispatchers.Unconfined)
        assertSame(Dispatchers.Unconfined, ioDispatcher())
    }
}
