/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/*
 * These tests hold strong references to every instance they read, so the weak
 * cache can never be collected mid-test. That keeps them deterministic on all
 * targets, including those where WeakRef.isWeakSupported is false.
 */
class WeakLazyTest {

    /** A payload with identity, so assertSame and assertNotSame are meaningful. */
    private class Payload

    @Test
    fun value_computes_once_and_is_stable_while_strongly_held() {
        var computations = 0
        val lazyPayload = weakLazy {
            computations++
            Payload()
        }

        val first = lazyPayload.value
        val second = lazyPayload.value

        assertSame(first, second)
        assertEquals(1, computations)
    }

    @Test
    fun peek_returns_null_before_computation_and_the_instance_after() {
        val lazyPayload = weakLazy { Payload() }
        assertNull(lazyPayload.peek())

        val computed = lazyPayload.value
        assertSame(computed, lazyPayload.peek())
    }

    @Test
    fun refresh_forces_a_new_instance() {
        var computations = 0
        val lazyPayload = weakLazy {
            computations++
            Payload()
        }

        val original = lazyPayload.value
        val refreshed = lazyPayload.refresh()

        assertNotSame(original, refreshed)
        assertEquals(2, computations)
        assertSame(refreshed, lazyPayload.value) // the refreshed instance is now cached
        assertEquals(2, computations)
    }

    @Test
    fun clear_drops_the_cache_and_the_next_read_recomputes() {
        var computations = 0
        val lazyPayload = weakLazy {
            computations++
            Payload()
        }

        val original = lazyPayload.value
        lazyPayload.clear()

        assertNull(lazyPayload.peek())
        val recomputed = lazyPayload.value
        assertNotSame(original, recomputed)
        assertEquals(2, computations)
    }

    @Test
    fun the_constructor_is_equivalent_to_the_factory() {
        val lazyPayload = WeakLazy { Payload() }
        assertSame(lazyPayload.value, lazyPayload.peek())
    }
}
