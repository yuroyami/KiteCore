/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagsTest {

    private val read = 1
    private val write = 2
    private val execute = 4

    @Test
    fun hasFlagRequiresAllBits() {
        val mask = read or write
        assertTrue(mask.hasFlag(read))
        assertTrue(mask.hasFlag(write))
        assertTrue(mask.hasFlag(read or write))
        assertFalse(mask.hasFlag(execute))
        assertFalse(mask.hasFlag(read or execute))
        assertTrue(mask.hasFlag(0))
    }

    @Test
    fun withFlagSetsBitsIdempotently() {
        assertEquals(read or write, read.withFlag(write))
        assertEquals(read, read.withFlag(read))
        assertEquals(read or write or execute, read.withFlag(write).withFlag(execute))
    }

    @Test
    fun withoutFlagClearsBitsIdempotently() {
        val mask = read or write or execute
        assertEquals(read or execute, mask.withoutFlag(write))
        assertEquals(mask, mask.withoutFlag(8))
        assertEquals(0, mask.withoutFlag(mask))
    }

    @Test
    fun toggleFlagInvertsBits() {
        val mask = read or write
        assertEquals(read, mask.toggleFlag(write))
        assertEquals(mask or execute, mask.toggleFlag(execute))
        assertEquals(mask, mask.toggleFlag(execute).toggleFlag(execute))
    }

    @Test
    fun decimalSizeConstantsMultiplyByPowersOfOneThousand() {
        assertEquals(2_000L, 2.KB)
        assertEquals(3_000_000L, 3.MB)
        assertEquals(1_000_000_000L, 1.GB)
        assertEquals(-1_000L, (-1).KB)
        assertEquals(2_147_483_647_000_000_000L, Int.MAX_VALUE.GB)
    }

    @Test
    fun binarySizeConstantsMultiplyByPowersOfTwo() {
        assertEquals(2_048L, 2.KiB)
        assertEquals(1_048_576L, 1.MiB)
        assertEquals(4_294_967_296L, 4.GiB)
        assertEquals(0L, 0.MiB)
    }
}
