/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.random

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RandomsTest {

    // ---------------------------------------------------------------- alphabets

    @Test
    fun alphabetsHaveExpectedSizesAndContents() {
        assertEquals(62, RandomAlphabets.ALPHANUMERIC.length)
        assertEquals(52, RandomAlphabets.ALPHABETIC.length)
        assertEquals(10, RandomAlphabets.DIGITS.length)
        assertEquals(16, RandomAlphabets.HEX_LOWER.length)

        assertEquals(RandomAlphabets.ALPHANUMERIC.length, RandomAlphabets.ALPHANUMERIC.toSet().size)
        assertEquals(RandomAlphabets.ALPHABETIC.length, RandomAlphabets.ALPHABETIC.toSet().size)
        assertEquals(RandomAlphabets.DIGITS.length, RandomAlphabets.DIGITS.toSet().size)
        assertEquals(RandomAlphabets.HEX_LOWER.length, RandomAlphabets.HEX_LOWER.toSet().size)

        assertTrue(RandomAlphabets.DIGITS.all { it in '0'..'9' })
        assertTrue(RandomAlphabets.HEX_LOWER.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(RandomAlphabets.ALPHABETIC.all { it in 'a'..'z' || it in 'A'..'Z' })
        assertTrue(RandomAlphabets.ALPHANUMERIC.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })
    }

    // ---------------------------------------------------------------- nextChar

    @Test
    fun nextCharDrawsFromAlphabet() {
        val random = Random(42)
        repeat(200) {
            val c = random.nextChar("abc")
            assertContains(listOf('a', 'b', 'c'), c)
        }
    }

    @Test
    fun nextCharThrowsOnEmptyAlphabet() {
        assertFailsWith<IllegalArgumentException> { Random(42).nextChar("") }
    }

    // ---------------------------------------------------------------- nextString

    @Test
    fun nextStringHasRequestedLengthAndAlphabetMembership() {
        val random = Random(42)
        val s = random.nextString(500, "xyz")
        assertEquals(500, s.length)
        assertTrue(s.all { it in "xyz" })
        // With 500 draws from a 3-letter alphabet, every letter appears.
        assertTrue('x' in s && 'y' in s && 'z' in s)
    }

    @Test
    fun nextStringZeroLengthYieldsEmptyString() {
        assertEquals("", Random(42).nextString(0, "abc"))
    }

    @Test
    fun nextStringIsDeterministicForSameSeed() {
        val a = Random(42).nextString(32, RandomAlphabets.ALPHANUMERIC)
        val b = Random(42).nextString(32, RandomAlphabets.ALPHANUMERIC)
        assertEquals(a, b)
    }

    @Test
    fun nextStringThrowsOnNegativeLength() {
        assertFailsWith<IllegalArgumentException> { Random(42).nextString(-1, "abc") }
    }

    @Test
    fun nextStringThrowsOnEmptyAlphabet() {
        assertFailsWith<IllegalArgumentException> { Random(42).nextString(5, "") }
    }

    // ------------------------------------------------- derived string generators

    @Test
    fun nextAlphanumericProducesOnlyAlphanumericCharacters() {
        val random = Random(42)
        val s = random.nextAlphanumeric(300)
        assertEquals(300, s.length)
        assertTrue(s.all { it in RandomAlphabets.ALPHANUMERIC })
        assertFailsWith<IllegalArgumentException> { random.nextAlphanumeric(-3) }
    }

    @Test
    fun nextAlphabeticProducesOnlyLetters() {
        val random = Random(42)
        val s = random.nextAlphabetic(300)
        assertEquals(300, s.length)
        assertTrue(s.all { it in 'a'..'z' || it in 'A'..'Z' })
        assertFailsWith<IllegalArgumentException> { random.nextAlphabetic(-1) }
    }

    @Test
    fun nextDigitsProducesOnlyDigits() {
        val random = Random(42)
        val s = random.nextDigits(300)
        assertEquals(300, s.length)
        assertTrue(s.all { it in '0'..'9' })
        assertFailsWith<IllegalArgumentException> { random.nextDigits(-1) }
    }

    @Test
    fun nextHexStringProducesOnlyLowercaseHex() {
        val random = Random(42)
        val s = random.nextHexString(300)
        assertEquals(300, s.length)
        assertTrue(s.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(s.none { it.isUpperCase() })
        assertFailsWith<IllegalArgumentException> { random.nextHexString(-1) }
    }

    // ---------------------------------------------------------------- nextBoolean

    @Test
    fun nextBooleanRespectsDegenerateProbabilities() {
        val random = Random(42)
        repeat(500) { assertEquals(false, random.nextBoolean(0.0)) }
        repeat(500) { assertEquals(true, random.nextBoolean(1.0)) }
    }

    @Test
    fun nextBooleanDistributionMatchesProbability() {
        val random = Random(42)
        val draws = 10_000
        val trueCount = (1..draws).count { random.nextBoolean(0.25) }
        // Expected 2500 with a standard deviation of about 43; the band is over 10 sigma wide.
        assertTrue(trueCount in 2000..3000, "trueCount was $trueCount, expected close to 2500")
    }

    @Test
    fun nextBooleanThrowsOnInvalidProbability() {
        assertFailsWith<IllegalArgumentException> { Random(42).nextBoolean(-0.01) }
        assertFailsWith<IllegalArgumentException> { Random(42).nextBoolean(1.01) }
        assertFailsWith<IllegalArgumentException> { Random(42).nextBoolean(Double.NaN) }
    }

    // ---------------------------------------------------------------- nextGaussian

    @Test
    fun nextGaussianMatchesRequestedMoments() {
        val random = Random(42)
        val n = 10_000
        val samples = DoubleArray(n) { random.nextGaussian(mean = 5.0, standardDeviation = 2.0) }
        val mean = samples.sum() / n
        val variance = samples.sumOf { (it - mean) * (it - mean) } / n
        val sd = sqrt(variance)
        // Standard error of the mean is 0.02, so a 0.2 band is 10 sigma wide.
        assertTrue(abs(mean - 5.0) < 0.2, "sample mean was $mean, expected close to 5.0")
        assertTrue(abs(sd - 2.0) < 0.2, "sample sd was $sd, expected close to 2.0")
    }

    @Test
    fun nextGaussianDefaultsToStandardNormal() {
        val random = Random(42)
        val n = 10_000
        val mean = (1..n).sumOf { random.nextGaussian() } / n
        assertTrue(abs(mean) < 0.1, "sample mean was $mean, expected close to 0.0")
    }

    @Test
    fun nextGaussianWithZeroDeviationReturnsMean() {
        val random = Random(42)
        repeat(20) { assertEquals(7.5, random.nextGaussian(mean = 7.5, standardDeviation = 0.0)) }
    }

    @Test
    fun nextGaussianThrowsOnNegativeDeviation() {
        assertFailsWith<IllegalArgumentException> { Random(42).nextGaussian(0.0, -1.0) }
    }

    // ---------------------------------------------------------------- gaussianSequence

    @Test
    fun gaussianSequenceYieldsFiniteSamplesAroundMean() {
        val random = Random(42)
        val samples = random.gaussianSequence(mean = 3.0, standardDeviation = 1.0).take(2_000).toList()
        assertEquals(2_000, samples.size)
        assertTrue(samples.all { it.isFinite() })
        val mean = samples.sum() / samples.size
        assertTrue(abs(mean - 3.0) < 0.25, "sample mean was $mean, expected close to 3.0")
    }

    @Test
    fun gaussianSequenceValidatesDeviationEagerly() {
        assertFailsWith<IllegalArgumentException> { Random(42).gaussianSequence(0.0, -0.5) }
    }

    // ---------------------------------------------------------------- nextSign

    @Test
    fun nextSignReturnsOnlyPlusOrMinusOneAndIsRoughlyFair() {
        val random = Random(42)
        val draws = 1_000
        var plus = 0
        repeat(draws) {
            val sign = random.nextSign()
            assertTrue(sign == 1 || sign == -1, "sign was $sign")
            if (sign == 1) plus++
        }
        // Expected 500 with a standard deviation of about 16; the band is over 6 sigma wide.
        assertTrue(plus in 400..600, "plus count was $plus, expected close to 500")
        assertTrue(plus in 1 until draws, "both signs must occur")
    }

    // ---------------------------------------------------------------- sample

    @Test
    fun sampleReturnsRequestedCountFromSource() {
        val source = (1..100).toList()
        val picked = source.sample(10, Random(42))
        assertEquals(10, picked.size)
        assertTrue(picked.all { it in source })
        assertEquals(10, picked.toSet().size, "positions are distinct so distinct values are expected here")
    }

    @Test
    fun sampleOfFullSizeIsAPermutation() {
        val source = listOf(1, 2, 3, 4, 5)
        val picked = source.sample(source.size, Random(42))
        assertEquals(source, picked.sorted())
    }

    @Test
    fun sampleZeroReturnsEmptyList() {
        assertEquals(emptyList(), listOf(1, 2, 3).sample(0, Random(42)))
        assertEquals(emptyList(), emptyList<Int>().sample(0, Random(42)))
    }

    @Test
    fun sampleIsDeterministicForSameSeed() {
        val source = (1..50).toList()
        assertEquals(source.sample(7, Random(42)), source.sample(7, Random(42)))
    }

    @Test
    fun sampleThrowsWhenCountIsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { listOf(1, 2, 3).sample(4, Random(42)) }
        assertFailsWith<IllegalArgumentException> { listOf(1, 2, 3).sample(-1, Random(42)) }
    }

    // ---------------------------------------------------------------- sampleOrNull

    @Test
    fun sampleOrNullMirrorsSampleForValidArguments() {
        val source = (1..20).toList()
        val viaSample = source.sample(5, Random(42))
        val viaOrNull = source.sampleOrNull(5, Random(42))
        assertNotNull(viaOrNull)
        assertEquals(viaSample, viaOrNull)
    }

    @Test
    fun sampleOrNullReturnsNullWhenCountIsOutOfRange() {
        assertNull(listOf(1, 2, 3).sampleOrNull(4, Random(42)))
        assertNull(listOf(1, 2, 3).sampleOrNull(-1, Random(42)))
    }

    // ---------------------------------------------------------------- sampleWithReplacement

    @Test
    fun sampleWithReplacementAllowsCountAboveSize() {
        val source = listOf("a", "b")
        val picked = source.sampleWithReplacement(50, Random(42))
        assertEquals(50, picked.size)
        assertTrue(picked.all { it in source })
        // With 50 draws from two values, both appear.
        assertEquals(setOf("a", "b"), picked.toSet())
    }

    @Test
    fun sampleWithReplacementFromSingletonRepeatsTheElement() {
        assertEquals(List(5) { 9 }, listOf(9).sampleWithReplacement(5, Random(42)))
    }

    @Test
    fun sampleWithReplacementZeroYieldsEmptyEvenForEmptySource() {
        assertEquals(emptyList(), emptyList<Int>().sampleWithReplacement(0, Random(42)))
    }

    @Test
    fun sampleWithReplacementThrowsOnInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { emptyList<Int>().sampleWithReplacement(1, Random(42)) }
        assertFailsWith<IllegalArgumentException> { listOf(1).sampleWithReplacement(-1, Random(42)) }
    }

    // ---------------------------------------------------------------- weightedRandomIndex

    @Test
    fun weightedRandomIndexAlwaysPicksTheOnlyPositiveWeight() {
        val random = Random(42)
        val items = listOf("a", "b", "c", "d")
        val weights = listOf(0.0, 0.0, 5.0, 0.0)
        repeat(200) { assertEquals(2, items.weightedRandomIndex(weights, random)) }
    }

    @Test
    fun weightedRandomIndexStaysWithinBounds() {
        val random = Random(42)
        val items = listOf(10, 20, 30)
        val weights = listOf(1.0, 2.0, 3.0)
        repeat(500) { assertTrue(items.weightedRandomIndex(weights, random) in items.indices) }
    }

    // ---------------------------------------------------------------- weightedRandom

    @Test
    fun weightedRandomAlwaysPicksTheOnlyPositiveWeight() {
        val random = Random(42)
        val items = listOf("a", "b", "c")
        repeat(200) { assertEquals("b", items.weightedRandom(listOf(0.0, 1.0, 0.0), random)) }
    }

    @Test
    fun weightedRandomFollowsTheWeightRatio() {
        val random = Random(42)
        val items = listOf("light", "heavy")
        val weights = listOf(1.0, 3.0)
        val draws = 10_000
        val heavyCount = (1..draws).count { items.weightedRandom(weights, random) == "heavy" }
        // Expected 7500 with a standard deviation of about 43; the band is over 10 sigma wide.
        assertTrue(heavyCount in 7000..8000, "heavyCount was $heavyCount, expected close to 7500")
    }

    @Test
    fun weightedRandomThrowsOnInvalidArguments() {
        val random = Random(42)
        assertFailsWith<IllegalArgumentException> { emptyList<String>().weightedRandom(emptyList(), random) }
        assertFailsWith<IllegalArgumentException> { listOf("a", "b").weightedRandom(listOf(1.0), random) }
        assertFailsWith<IllegalArgumentException> { listOf("a", "b").weightedRandom(listOf(0.0, 0.0), random) }
        assertFailsWith<IllegalArgumentException> { listOf("a", "b").weightedRandom(listOf(-1.0, 2.0), random) }
        assertFailsWith<IllegalArgumentException> { listOf("a", "b").weightedRandom(listOf(Double.NaN, 1.0), random) }
        assertFailsWith<IllegalArgumentException> {
            listOf("a", "b").weightedRandom(listOf(Double.POSITIVE_INFINITY, 1.0), random)
        }
    }

    // ---------------------------------------------------------------- weightedRandomOrNull

    @Test
    fun weightedRandomOrNullMatchesWeightedRandomForValidArguments() {
        val items = listOf("a", "b", "c")
        val weights = listOf(1.0, 2.0, 3.0)
        val throwing = items.weightedRandom(weights, Random(42))
        val nullable = items.weightedRandomOrNull(weights, Random(42))
        assertEquals(throwing, nullable)
    }

    @Test
    fun weightedRandomOrNullReturnsNullOnInvalidArguments() {
        val random = Random(42)
        assertNull(emptyList<String>().weightedRandomOrNull(emptyList(), random))
        assertNull(listOf("a", "b").weightedRandomOrNull(listOf(1.0), random))
        assertNull(listOf("a", "b").weightedRandomOrNull(listOf(0.0, 0.0), random))
        assertNull(listOf("a", "b").weightedRandomOrNull(listOf(-1.0, 2.0), random))
        assertNull(listOf("a", "b").weightedRandomOrNull(listOf(Double.NaN, 1.0), random))
    }

    // ---------------------------------------------------------------- randomOf

    @Test
    fun randomOfReturnsOneOfTheOptions() {
        val random = Random(42)
        repeat(200) {
            val picked = randomOf("red", "green", "blue", random = random)
            assertContains(listOf("red", "green", "blue"), picked)
        }
    }

    @Test
    fun randomOfSingleOptionReturnsIt() {
        assertEquals(42, randomOf(42, random = Random(42)))
    }

    @Test
    fun randomOfEventuallyReturnsEveryOption() {
        val random = Random(42)
        val seen = mutableSetOf<Int>()
        repeat(500) { seen += randomOf(1, 2, 3, random = random) }
        assertEquals(setOf(1, 2, 3), seen)
    }

    @Test
    fun randomOfThrowsOnEmptyOptions() {
        assertFailsWith<IllegalArgumentException> { randomOf<Int>(random = Random(42)) }
    }
}
