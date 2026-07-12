/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormatTest {

    @Test
    fun roundToRoundsHalfUpAwayFromZero() {
        assertEquals(3.14, 3.14159.roundTo(2))
        assertEquals(3.0, 2.5.roundTo(0))
        assertEquals(-3.0, (-2.5).roundTo(0))
        assertEquals(1.5, 1.5.roundTo(3))
        assertTrue(Double.NaN.roundTo(2).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY.roundTo(2))
        assertFailsWith<IllegalArgumentException> { 1.0.roundTo(-1) }
        assertFailsWith<IllegalArgumentException> { 1.0.roundTo(16) }
    }

    @Test
    fun roundToFloatDelegatesToDouble() {
        assertEquals(3.14f, 3.14159f.roundTo(2))
        assertEquals(-3.0f, (-2.5f).roundTo(0))
    }

    @Test
    fun floorToRoundsTowardNegativeInfinity() {
        assertEquals(3.7, 3.75.floorTo(1))
        assertEquals(-3.2, (-3.11).floorTo(1))
        assertEquals(3.0, 3.75.floorTo(0))
    }

    @Test
    fun ceilToRoundsTowardPositiveInfinity() {
        assertEquals(3.3, 3.25.ceilTo(1))
        assertEquals(-3.7, (-3.75).ceilTo(1))
        assertEquals(4.0, 3.25.ceilTo(0))
    }

    @Test
    fun formatDecimalsCarriesAndPads() {
        assertEquals("10.0", 9.99.formatDecimals(1))
        assertEquals("3.14", 3.14159.formatDecimals(2))
        assertEquals("1.500", 1.5.formatDecimals(3))
        assertEquals("0.07", 0.07.formatDecimals(2))
        assertEquals("42", 42.0.formatDecimals(0))
        assertEquals("1", 0.5.formatDecimals(0))
        assertEquals("-3", (-2.5).formatDecimals(0))
        assertEquals("0.0", (-0.04).formatDecimals(1))
        assertEquals("NaN", Double.NaN.formatDecimals(2))
        assertEquals("Infinity", Double.POSITIVE_INFINITY.formatDecimals(2))
        assertEquals("-Infinity", Double.NEGATIVE_INFINITY.formatDecimals(2))
        assertFailsWith<IllegalArgumentException> { 1.0.formatDecimals(-1) }
        assertFailsWith<IllegalArgumentException> { 1.0e18.formatDecimals(15) }
    }

    @Test
    fun formatDecimalsFloatDelegatesToDouble() {
        assertEquals("2.5", 2.5f.formatDecimals(1))
        assertEquals("10.0", 9.99f.formatDecimals(1))
    }

    @Test
    fun roundToNearestDoubleSnapsToStep() {
        assertEquals(7.5, 7.3.roundToNearest(2.5))
        assertEquals(1.5, 1.25.roundToNearest(0.5))
        assertEquals(-7.5, (-7.3).roundToNearest(2.5))
        assertEquals(0.0, 0.1.roundToNearest(2.5))
        assertFailsWith<IllegalArgumentException> { 1.0.roundToNearest(0.0) }
        assertFailsWith<IllegalArgumentException> { 1.0.roundToNearest(-2.0) }
    }

    @Test
    fun roundToNearestIntSnapsToStep() {
        assertEquals(5, 7.roundToNearest(5))
        assertEquals(10, 8.roundToNearest(5))
        assertEquals(30, 25.roundToNearest(10))
        assertEquals(-30, (-25).roundToNearest(10))
        assertEquals(0, 2.roundToNearest(10))
        assertFailsWith<IllegalArgumentException> { 5.roundToNearest(0) }
        assertFailsWith<ArithmeticException> { Int.MAX_VALUE.roundToNearest(10) }
    }

    @Test
    fun formatGroupedLongInsertsSeparators() {
        assertEquals("1,234,567", 1_234_567L.formatGrouped())
        assertEquals("123", 123L.formatGrouped())
        assertEquals("-1,234,567", (-1_234_567L).formatGrouped())
        assertEquals("1_234_567_890_123", 1_234_567_890_123L.formatGrouped('_'))
        assertEquals("-9,223,372,036,854,775,808", Long.MIN_VALUE.formatGrouped())
    }

    @Test
    fun formatGroupedIntDelegatesToLong() {
        assertEquals("1,234,567", 1_234_567.formatGrouped())
        assertEquals("-2,147,483,648", Int.MIN_VALUE.formatGrouped())
        assertEquals("0", 0.formatGrouped())
    }

    @Test
    fun formatBytesSelectsUnitAndBase() {
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("512 B", 512L.formatBytes())
        assertEquals("1.5 KiB", 1536L.formatBytes())
        assertEquals("1.5 kB", 1536L.formatBytes(si = true))
        assertEquals("1.00 MiB", 1_048_576L.formatBytes(decimals = 2))
        assertEquals("1.0 TiB", 1_099_511_627_776L.formatBytes())
        assertEquals("-2.0 KiB", (-2048L).formatBytes())
        assertEquals("-8.0 EiB", Long.MIN_VALUE.formatBytes())
        assertEquals("2.0 kB", 2_000L.formatBytes(si = true))
        assertFailsWith<IllegalArgumentException> { 1L.formatBytes(decimals = -1) }
    }

    @Test
    fun formatPercentScalesByOneHundred() {
        assertEquals("42%", 0.42.formatPercent())
        assertEquals("12.5%", 0.125.formatPercent(1))
        assertEquals("100%", 1.0.formatPercent())
        assertEquals("-5%", (-0.05).formatPercent())
        assertEquals("0.50%", 0.005.formatPercent(2))
    }
}
