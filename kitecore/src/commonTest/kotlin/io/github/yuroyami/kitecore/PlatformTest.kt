/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun platform_is_populated() {
        assertTrue(platform.name.isNotBlank(), "platform.name should be set")
        assertTrue(platform.toString().isNotBlank())
        // family is a non-null enum by construction; touch it so the test fails to
        // compile if the shape regresses.
        val f: PlatformFamily = platform.family
        assertTrue(f in PlatformFamily.entries)
    }

    @Test
    fun version_is_exposed() {
        assertTrue(KiteCore.VERSION.isNotBlank())
    }
}
