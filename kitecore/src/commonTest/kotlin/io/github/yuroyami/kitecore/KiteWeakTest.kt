/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KiteWeakTest {

    @Test
    fun get_returns_the_referent_while_strongly_held() {
        val target = StringBuilder("kite")
        val weak = KiteWeak(target)
        assertSame(target, weak.get()) // identity preserved on every target
    }

    @Test
    fun clear_nulls_the_reference_on_every_target() {
        val target = Any()
        val weak = KiteWeak(target)
        assertTrue(weak.get() != null)
        weak.clear()
        assertNull(weak.get())
        weak.clear() // idempotent
        assertNull(weak.get())
    }

    @Test
    fun primitive_referents_are_safe() {
        // JS primitives are not valid WeakRef targets; the js actual must fall
        // back to a strong hold instead of throwing. No-op elsewhere.
        val weak = KiteWeak("kite")
        assertEquals("kite", weak.get())
        weak.clear()
        assertNull(weak.get())
    }

    @Test
    fun isWeakSupported_is_false_only_on_wasm() {
        // Ties the capability flag to the existing Platform identity API.
        val expected = Platform.current.family != PlatformFamily.WASM
        assertEquals(expected, KiteWeak.isWeakSupported)
    }
}
