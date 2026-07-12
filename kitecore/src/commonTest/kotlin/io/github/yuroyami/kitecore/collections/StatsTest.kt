/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StatsTest {

    private val tolerance = 1e-9

    // -- Median ----------------------------------------------------------------

    @Test
    fun median_of_doubles_handles_odd_and_even_sizes() {
        assertEquals(2.0, listOf(3.0, 1.0, 2.0).median(), tolerance)
        assertEquals(2.5, listOf(4.0, 1.0, 3.0, 2.0).median(), tolerance)
        assertEquals(7.0, listOf(7.0).median(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Double>().median() }
    }

    @Test
    fun medianOrNull_of_doubles_returns_null_on_empty() {
        assertEquals(2.0, listOf(1.0, 2.0, 3.0).medianOrNull()!!, tolerance)
        assertNull(emptyList<Double>().medianOrNull())
    }

    @Test
    fun median_of_ints_delegates_to_doubles() {
        assertEquals(2.5, listOf(4, 1, 3, 2).median(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Int>().median() }
    }

    @Test
    fun medianOrNull_of_ints_returns_null_on_empty() {
        assertEquals(2.0, listOf(3, 1, 2).medianOrNull()!!, tolerance)
        assertNull(emptyList<Int>().medianOrNull())
    }

    @Test
    fun median_of_longs_delegates_to_doubles() {
        assertEquals(2.0, listOf(3L, 1L, 2L).median(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Long>().median() }
    }

    @Test
    fun medianOrNull_of_longs_returns_null_on_empty() {
        assertEquals(1.5, listOf(2L, 1L).medianOrNull()!!, tolerance)
        assertNull(emptyList<Long>().medianOrNull())
    }

    // -- Variance ----------------------------------------------------------------

    @Test
    fun variance_of_doubles_is_the_population_variance() {
        assertEquals(4.0, listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0).variance(), tolerance)
        assertEquals(0.0, listOf(5.0).variance(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Double>().variance() }
    }

    @Test
    fun varianceOrNull_of_doubles_returns_null_on_empty() {
        assertEquals(0.25, listOf(1.0, 2.0).varianceOrNull()!!, tolerance)
        assertNull(emptyList<Double>().varianceOrNull())
    }

    @Test
    fun variance_of_ints_delegates_to_doubles() {
        assertEquals(2.0 / 3.0, listOf(1, 2, 3).variance(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Int>().variance() }
    }

    @Test
    fun varianceOrNull_of_ints_returns_null_on_empty() {
        assertEquals(0.0, listOf(4, 4).varianceOrNull()!!, tolerance)
        assertNull(emptyList<Int>().varianceOrNull())
    }

    @Test
    fun variance_of_longs_delegates_to_doubles() {
        assertEquals(2.0 / 3.0, listOf(1L, 2L, 3L).variance(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Long>().variance() }
    }

    @Test
    fun varianceOrNull_of_longs_returns_null_on_empty() {
        assertEquals(0.25, listOf(1L, 2L).varianceOrNull()!!, tolerance)
        assertNull(emptyList<Long>().varianceOrNull())
    }

    // -- Standard deviation ---------------------------------------------------------

    @Test
    fun stdDev_of_doubles_is_the_square_root_of_variance() {
        assertEquals(2.0, listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0).stdDev(), tolerance)
        assertEquals(0.0, listOf(3.0).stdDev(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Double>().stdDev() }
    }

    @Test
    fun stdDevOrNull_of_doubles_returns_null_on_empty() {
        assertEquals(0.5, listOf(1.0, 2.0).stdDevOrNull()!!, tolerance)
        assertNull(emptyList<Double>().stdDevOrNull())
    }

    @Test
    fun stdDev_of_ints_delegates_to_doubles() {
        assertEquals(2.0, listOf(2, 4, 4, 4, 5, 5, 7, 9).stdDev(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Int>().stdDev() }
    }

    @Test
    fun stdDevOrNull_of_ints_returns_null_on_empty() {
        assertEquals(0.5, listOf(1, 2).stdDevOrNull()!!, tolerance)
        assertNull(emptyList<Int>().stdDevOrNull())
    }

    @Test
    fun stdDev_of_longs_delegates_to_doubles() {
        assertEquals(2.0, listOf(2L, 4L, 4L, 4L, 5L, 5L, 7L, 9L).stdDev(), tolerance)
        assertFailsWith<NoSuchElementException> { emptyList<Long>().stdDev() }
    }

    @Test
    fun stdDevOrNull_of_longs_returns_null_on_empty() {
        assertEquals(0.5, listOf(1L, 2L).stdDevOrNull()!!, tolerance)
        assertNull(emptyList<Long>().stdDevOrNull())
    }

    // -- Percentile ---------------------------------------------------------------------

    @Test
    fun percentile_of_doubles_interpolates_linearly() {
        val data = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, data.percentile(0.0), tolerance)
        assertEquals(3.0, data.percentile(50.0), tolerance)
        assertEquals(4.6, data.percentile(90.0), tolerance)
        assertEquals(5.0, data.percentile(100.0), tolerance)
        assertEquals(2.0, listOf(2.0).percentile(75.0), tolerance)
    }

    @Test
    fun percentile_of_doubles_rejects_bad_input() {
        assertFailsWith<IllegalArgumentException> { listOf(1.0).percentile(-0.1) }
        assertFailsWith<IllegalArgumentException> { listOf(1.0).percentile(100.1) }
        assertFailsWith<NoSuchElementException> { emptyList<Double>().percentile(50.0) }
    }

    @Test
    fun percentileOrNull_of_doubles_returns_null_on_empty_but_still_validates_p() {
        assertEquals(3.0, listOf(1.0, 3.0, 5.0).percentileOrNull(50.0)!!, tolerance)
        assertNull(emptyList<Double>().percentileOrNull(50.0))
        assertFailsWith<IllegalArgumentException> { emptyList<Double>().percentileOrNull(101.0) }
    }

    @Test
    fun percentile_of_ints_delegates_to_doubles() {
        assertEquals(2.5, listOf(1, 2, 3, 4).percentile(50.0), tolerance)
        assertFailsWith<IllegalArgumentException> { listOf(1).percentile(-1.0) }
        assertFailsWith<NoSuchElementException> { emptyList<Int>().percentile(50.0) }
    }

    @Test
    fun percentileOrNull_of_ints_returns_null_on_empty_but_still_validates_p() {
        assertEquals(4.6, listOf(1, 2, 3, 4, 5).percentileOrNull(90.0)!!, tolerance)
        assertNull(emptyList<Int>().percentileOrNull(50.0))
        assertFailsWith<IllegalArgumentException> { emptyList<Int>().percentileOrNull(-1.0) }
    }

    @Test
    fun percentile_of_longs_delegates_to_doubles() {
        assertEquals(2.5, listOf(1L, 2L, 3L, 4L).percentile(50.0), tolerance)
        assertFailsWith<IllegalArgumentException> { listOf(1L).percentile(100.5) }
        assertFailsWith<NoSuchElementException> { emptyList<Long>().percentile(50.0) }
    }

    @Test
    fun percentileOrNull_of_longs_returns_null_on_empty_but_still_validates_p() {
        assertEquals(1.0, listOf(1L, 1L, 1L).percentileOrNull(25.0)!!, tolerance)
        assertNull(emptyList<Long>().percentileOrNull(50.0))
        assertFailsWith<IllegalArgumentException> { emptyList<Long>().percentileOrNull(-5.0) }
    }
}
