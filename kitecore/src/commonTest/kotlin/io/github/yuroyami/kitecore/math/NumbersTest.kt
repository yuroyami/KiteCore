/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumbersTest {

    @Test
    fun lerpDoubleInterpolatesAndExtrapolates() {
        assertEquals(2.0, lerp(2.0, 4.0, 0.0))
        assertEquals(4.0, lerp(2.0, 4.0, 1.0))
        assertEquals(3.0, lerp(2.0, 4.0, 0.5))
        assertEquals(6.0, lerp(2.0, 4.0, 2.0))
    }

    @Test
    fun lerpFloatInterpolates() {
        assertEquals(1.5f, lerp(1.0f, 3.0f, 0.25f))
        assertEquals(3.0f, lerp(1.0f, 3.0f, 1.0f))
    }

    @Test
    fun inverseLerpReturnsFraction() {
        assertEquals(0.25, inverseLerp(0.0, 10.0, 2.5))
        assertEquals(0.0, inverseLerp(5.0, 5.0, 7.0))
        assertEquals(-0.5, inverseLerp(0.0, 10.0, -5.0))
    }

    @Test
    fun remapMovesValueBetweenRanges() {
        assertEquals(50.0, 5.0.remap(0.0, 10.0, 0.0, 100.0))
        assertEquals(0.0, 0.0.remap(0.0, 10.0, 0.0, 100.0))
        assertEquals(30.0, 7.0.remap(0.0, 10.0, 100.0, 0.0))
    }

    @Test
    fun evenAndOddClassifyIntegers() {
        assertTrue(4.isEven())
        assertFalse(3.isEven())
        assertTrue(3.isOdd())
        assertFalse(4.isOdd())
        assertTrue((-2).isEven())
        assertTrue((-3).isOdd())
        assertTrue(4L.isEven())
        assertFalse(3L.isEven())
        assertTrue(3L.isOdd())
        assertFalse(4L.isOdd())
    }

    @Test
    fun digitsReturnsAbsoluteDigitsMostSignificantFirst() {
        assertEquals(listOf(4, 7, 2, 8), 4728.digits())
        assertEquals(listOf(3, 0, 5), (-305).digits())
        assertEquals(listOf(0), 0.digits())
        assertEquals(10, Int.MIN_VALUE.digits().size)
        assertEquals(2, Int.MIN_VALUE.digits().first())
    }

    @Test
    fun digitCountCountsDecimalDigits() {
        assertEquals(1, 0.digitCount())
        assertEquals(5, (-12345).digitCount())
        assertEquals(10, Int.MIN_VALUE.digitCount())
        assertEquals(1, 0L.digitCount())
        assertEquals(12, 123_456_789_012L.digitCount())
        assertEquals(19, Long.MIN_VALUE.digitCount())
    }

    @Test
    fun gcdIntHandlesSignsAndZero() {
        assertEquals(6, gcd(12, 18))
        assertEquals(2, gcd(-4, 6))
        assertEquals(5, gcd(0, 5))
        assertEquals(0, gcd(0, 0))
        assertFailsWith<ArithmeticException> { gcd(Int.MIN_VALUE, 0) }
    }

    @Test
    fun gcdLongHandlesSignsAndZero() {
        assertEquals(12L, gcd(48L, 180L))
        assertEquals(3L, gcd(-9L, 6L))
        assertEquals(0L, gcd(0L, 0L))
        assertFailsWith<ArithmeticException> { gcd(Long.MIN_VALUE, 0L) }
    }

    @Test
    fun lcmIntNeverOverflows() {
        assertEquals(12L, lcm(4, 6))
        assertEquals(0L, lcm(0, 5))
        assertEquals(12L, lcm(-4, 6))
        assertEquals(2_147_483_648L, lcm(Int.MIN_VALUE, 1))
    }

    @Test
    fun lcmLongThrowsOnOverflow() {
        assertEquals(12L, lcm(4L, 6L))
        assertEquals(0L, lcm(0L, 7L))
        assertFailsWith<ArithmeticException> { lcm(Long.MAX_VALUE, Long.MAX_VALUE - 1L) }
    }

    @Test
    fun powComputesCheckedIntegerPowers() {
        assertEquals(1024L, 2.pow(10))
        assertEquals(1L, 5.pow(0))
        assertEquals(1L, 0.pow(0))
        assertEquals(-27L, (-3).pow(3))
        assertFailsWith<IllegalArgumentException> { 2.pow(-1) }
        assertFailsWith<ArithmeticException> { 2.pow(64) }
    }

    @Test
    fun factorialCoversFullDomain() {
        assertEquals(1L, factorial(0))
        assertEquals(120L, factorial(5))
        assertEquals(2_432_902_008_176_640_000L, factorial(20))
        assertFailsWith<IllegalArgumentException> { factorial(-1) }
        assertFailsWith<IllegalArgumentException> { factorial(21) }
    }

    @Test
    fun isPrimeUsesTrialDivision() {
        assertFalse(isPrime(-7L))
        assertFalse(isPrime(0L))
        assertFalse(isPrime(1L))
        assertTrue(isPrime(2L))
        assertTrue(isPrime(3L))
        assertFalse(isPrime(25L))
        assertFalse(isPrime(49L))
        assertTrue(isPrime(97L))
        assertTrue(isPrime(7919L))
        assertFalse(isPrime(7917L))
        assertTrue(isPrime(1_000_000_007L))
    }

    @Test
    fun powerOfTwoChecksPositivePowers() {
        assertTrue(1.isPowerOfTwo())
        assertTrue(1024.isPowerOfTwo())
        assertFalse(0.isPowerOfTwo())
        assertFalse((-2).isPowerOfTwo())
        assertFalse(3.isPowerOfTwo())
        assertTrue(4096L.isPowerOfTwo())
        assertFalse(0L.isPowerOfTwo())
        assertFalse(Long.MIN_VALUE.isPowerOfTwo())
    }

    @Test
    fun nextPowerOfTwoRoundsUpward() {
        assertEquals(1, 0.nextPowerOfTwo())
        assertEquals(1, (-5).nextPowerOfTwo())
        assertEquals(1, 1.nextPowerOfTwo())
        assertEquals(8, 5.nextPowerOfTwo())
        assertEquals(8, 8.nextPowerOfTwo())
        assertEquals(2048, 1025.nextPowerOfTwo())
        assertEquals(1 shl 30, (1 shl 30).nextPowerOfTwo())
        assertFailsWith<ArithmeticException> { ((1 shl 30) + 1).nextPowerOfTwo() }
    }

    @Test
    fun ordinalSuffixFollowsEnglishRules() {
        assertEquals("st", 1.ordinalSuffix())
        assertEquals("nd", 2.ordinalSuffix())
        assertEquals("rd", 3.ordinalSuffix())
        assertEquals("th", 4.ordinalSuffix())
        assertEquals("th", 11.ordinalSuffix())
        assertEquals("th", 12.ordinalSuffix())
        assertEquals("th", 13.ordinalSuffix())
        assertEquals("st", 21.ordinalSuffix())
        assertEquals("th", 111.ordinalSuffix())
        assertEquals("st", 101.ordinalSuffix())
        assertEquals("nd", (-2).ordinalSuffix())
    }

    @Test
    fun withOrdinalSuffixAppendsSuffix() {
        assertEquals("21st", 21.withOrdinalSuffix())
        assertEquals("13th", 13.withOrdinalSuffix())
        assertEquals("-2nd", (-2).withOrdinalSuffix())
    }

    @Test
    fun angleConversionsRoundTrip() {
        assertTrue(180.0.toRadians().isApproximately(PI))
        assertTrue(90.0.toRadians().isApproximately(PI / 2.0))
        assertTrue(PI.toDegrees().isApproximately(180.0))
        assertTrue(0.0.toRadians().isApproximately(0.0))
    }

    @Test
    fun isApproximatelyComparesWithinEpsilon() {
        assertTrue(0.1.plus(0.2).isApproximately(0.3))
        assertFalse(1.0.isApproximately(1.1))
        assertTrue(1.0.isApproximately(1.4, epsilon = 0.5))
        assertTrue(Double.POSITIVE_INFINITY.isApproximately(Double.POSITIVE_INFINITY))
        assertFalse(Double.NaN.isApproximately(Double.NaN))
        assertTrue(0.1f.plus(0.2f).isApproximately(0.3f))
        assertFalse(1.0f.isApproximately(1.1f))
    }

    @Test
    fun percentOfComputesPercentageSafely() {
        assertEquals(50.0, 25.percentOf(50))
        assertEquals(200.0, 10.percentOf(5))
        assertEquals(0.0, 7.percentOf(0))
        assertTrue(1.percentOf(3).isApproximately(33.333333333, epsilon = 1e-6))
    }

    @Test
    fun clamp01ClampsIntoTheUnitRange() {
        assertEquals(0.0, (-2.0).clamp01())
        assertEquals(1.0, 7.5.clamp01())
        assertEquals(0.25, 0.25.clamp01())
        assertEquals(0f, (-1f).clamp01())
        assertEquals(1f, 2f.clamp01())
    }

    @Test
    fun midpointAvoidsOverflow() {
        assertEquals(2, 1.midpoint(3))
        assertEquals(Int.MAX_VALUE - 1, (Int.MAX_VALUE - 2).midpoint(Int.MAX_VALUE))
        assertEquals(0L, (-5L).midpoint(5L))
        assertEquals(Long.MAX_VALUE - 1, (Long.MAX_VALUE - 2).midpoint(Long.MAX_VALUE))
    }
}
