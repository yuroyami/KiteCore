/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun platform_is_populated() {
        val p = Platform.current
        assertTrue(p.name.isNotBlank(), "platform.name should be set")
        assertTrue(p.toString().startsWith(p.name))
        assertTrue(p.toString().endsWith("(${p.deviceModel})"))
    }

    @Test
    fun osVersion_does_not_duplicate_the_name() {
        val p = Platform.current
        assertFalse(p.osVersion.startsWith(p.name), "osVersion must not repeat name: $p")
    }

    @Test
    fun version_is_exposed() {
        assertTrue(KiteCore.VERSION.isNotBlank())
    }
}
