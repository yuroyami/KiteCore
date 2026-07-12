/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoizeTest {

    @Test
    fun unaryMemoizeReturnsCorrectResults() {
        val square = memoize { x: Int -> x * x }
        assertEquals(4, square(2))
        assertEquals(9, square(3))
        assertEquals(4, square(2))
    }

    @Test
    fun unaryMemoizeInvokesTheFunctionOncePerArgument() {
        var calls = 0
        val cached = memoize { x: Int -> calls++; x + 1 }
        cached(1)
        cached(1)
        cached(2)
        cached(2)
        cached(1)
        assertEquals(2, calls)
    }

    @Test
    fun unaryMemoizeCachesNullResults() {
        var calls = 0
        val cached = memoize { _: Int ->
            calls++
            null as String?
        }
        assertNull(cached(1))
        assertNull(cached(1))
        assertEquals(1, calls)
    }

    @Test
    fun binaryMemoizeReturnsCorrectResults() {
        val concat = memoize { a: String, b: String -> a + b }
        assertEquals("kitecore", concat("kite", "core"))
        assertEquals("corekite", concat("core", "kite"))
    }

    @Test
    fun binaryMemoizeInvokesTheFunctionOncePerArgumentPair() {
        var calls = 0
        val cached = memoize { a: Int, b: Int -> calls++; a + b }
        cached(1, 2)
        cached(1, 2)
        cached(2, 1)
        cached(2, 1)
        assertEquals(2, calls)
    }

    @Test
    fun binaryMemoizeCachesNullResults() {
        var calls = 0
        val cached = memoize { _: Int, _: Int ->
            calls++
            null as String?
        }
        assertNull(cached(1, 2))
        assertNull(cached(1, 2))
        assertEquals(1, calls)
    }
}
