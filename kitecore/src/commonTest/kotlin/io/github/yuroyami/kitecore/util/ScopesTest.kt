/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScopesTest {

    @Test
    fun applyIf_runs_the_block_only_when_the_condition_holds() {
        assertEquals("ab", StringBuilder("a").applyIf(true) { append("b") }.toString())
        assertEquals("a", StringBuilder("a").applyIf(false) { append("b") }.toString())
    }

    @Test
    fun applyIf_returns_the_receiver_itself() {
        val receiver = StringBuilder("a")
        assertSame(receiver, receiver.applyIf(true) { append("b") })
        assertSame(receiver, receiver.applyIf(false) { append("c") })
    }

    @Test
    fun applyUnless_runs_the_block_only_when_the_condition_fails() {
        assertEquals("a", StringBuilder("a").applyUnless(true) { append("b") }.toString())
        assertEquals("ab", StringBuilder("a").applyUnless(false) { append("b") }.toString())
    }

    @Test
    fun alsoIf_passes_the_receiver_and_respects_the_condition() {
        val receiver = StringBuilder("kite")
        var seen: StringBuilder? = null
        val result = receiver.alsoIf(true) { seen = it }
        assertSame(receiver, seen)
        assertSame(receiver, result)

        seen = null
        receiver.alsoIf(false) { seen = it }
        assertEquals(null, seen)
    }

    @Test
    fun transformIf_replaces_the_value_only_when_the_condition_holds() {
        assertEquals(6, 3.transformIf(true) { it * 2 })
        assertEquals(3, 3.transformIf(false) { it * 2 })
    }

    @Test
    fun orFalse_defaults_null_to_false() {
        val absent: Boolean? = null
        val presentTrue: Boolean? = true
        val presentFalse: Boolean? = false
        assertFalse(absent.orFalse())
        assertTrue(presentTrue.orFalse())
        assertFalse(presentFalse.orFalse())
    }

    @Test
    fun orTrue_defaults_null_to_true() {
        val absent: Boolean? = null
        val presentTrue: Boolean? = true
        val presentFalse: Boolean? = false
        assertTrue(absent.orTrue())
        assertFalse(presentFalse.orTrue())
        assertTrue(presentTrue.orTrue())
    }

    @Test
    fun toInt_maps_true_to_one_and_false_to_zero() {
        assertEquals(1, true.toInt())
        assertEquals(0, false.toInt())
        assertEquals(2, listOf(true, false, true).sumOf { it.toInt() })
    }
}
