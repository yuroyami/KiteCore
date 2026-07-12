/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResultsTest {

    /** An [Error] subclass used to verify that non-Exception throwables propagate. */
    private class TestError : Error("test error")

    @Test
    fun tryOrNull_returns_the_value_or_null_on_exception() {
        assertEquals(3, tryOrNull { 3 })
        assertNull(tryOrNull<Int> { throw IllegalStateException("boom") })
    }

    @Test
    fun tryOrNull_lets_errors_propagate() {
        assertFailsWith<TestError> { tryOrNull<Int> { throw TestError() } }
    }

    @Test
    fun tryOrDefault_returns_the_value_or_the_default_on_exception() {
        assertEquals(7, tryOrDefault(0) { 7 })
        assertEquals(0, tryOrDefault(0) { throw IllegalStateException("boom") })
    }

    @Test
    fun tryOrDefault_lets_errors_propagate() {
        assertFailsWith<TestError> { tryOrDefault(0) { throw TestError() } }
    }

    @Test
    fun tryOrElse_returns_the_value_or_maps_the_exception() {
        assertEquals("ok", tryOrElse({ "fell back" }) { "ok" })
        assertEquals(
            "recovered:boom",
            tryOrElse({ "recovered:${it.message}" }) { throw IllegalStateException("boom") },
        )
    }

    @Test
    fun tryOrElse_lets_errors_propagate() {
        assertFailsWith<TestError> { tryOrElse({ "unreachable" }) { throw TestError() } }
    }

    @Test
    fun flatMap_chains_successes() {
        val result = Result.success(2).flatMap { Result.success(it * 2) }
        assertEquals(Result.success(4), result)
    }

    @Test
    fun flatMap_returns_the_failure_produced_by_the_transform() {
        val boom = IllegalStateException("boom")
        val result = Result.success(2).flatMap { Result.failure<Int>(boom) }
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun flatMap_short_circuits_on_failure_without_invoking_the_transform() {
        val boom = IllegalStateException("boom")
        var invoked = false
        val result = Result.failure<Int>(boom).flatMap {
            invoked = true
            Result.success(it)
        }
        assertFalse(invoked)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun flatten_collapses_one_level_of_nesting() {
        assertEquals(Result.success(1), Result.success(Result.success(1)).flatten())

        val inner = IllegalStateException("inner")
        assertSame(inner, Result.success(Result.failure<Int>(inner)).flatten().exceptionOrNull())

        val outer = IllegalStateException("outer")
        assertSame(outer, Result.failure<Result<Int>>(outer).flatten().exceptionOrNull())
    }

    @Test
    fun mapFailure_transforms_only_the_failure_side() {
        assertEquals(Result.success(5), Result.success(5).mapFailure { IllegalStateException(it) })

        val cause = IllegalStateException("low level")
        val mapped = Result.failure<Int>(cause).mapFailure { IllegalArgumentException("domain", it) }
        val exception = assertIs<IllegalArgumentException>(mapped.exceptionOrNull())
        assertSame(cause, exception.cause)
    }

    @Test
    fun zip_combines_two_successes() {
        val result = Result.success(1).zip(Result.success(2)) { a, b -> a + b }
        assertEquals(Result.success(3), result)
    }

    @Test
    fun zip_prefers_the_first_failure_and_skips_the_combiner() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")
        var combined = false

        val bothFail = Result.failure<Int>(first).zip(Result.failure<Int>(second)) { _, _ ->
            combined = true
        }
        assertSame(first, bothFail.exceptionOrNull())
        assertFalse(combined)

        val secondFails = Result.success(1).zip(Result.failure<Int>(second)) { _, _ ->
            combined = true
        }
        assertSame(second, secondFails.exceptionOrNull())
        assertFalse(combined)
    }

    @Test
    fun three_way_zip_combines_successes_and_prefers_the_earliest_failure() {
        val combined = Result.success(1).zip(Result.success(2), Result.success(3)) { a, b, c ->
            a + b + c
        }
        assertEquals(Result.success(6), combined)

        val middle = IllegalStateException("middle")
        val failed = Result.success(1).zip(Result.failure<Int>(middle), Result.success(3)) { a, b, c ->
            a + b + c
        }
        assertSame(middle, failed.exceptionOrNull())
    }

    @Test
    fun isSuccessAnd_tests_the_success_value() {
        assertTrue(Result.success(4).isSuccessAnd { it > 3 })
        assertFalse(Result.success(4).isSuccessAnd { it > 5 })

        var invoked = false
        assertFalse(
            Result.failure<Int>(IllegalStateException("boom")).isSuccessAnd {
                invoked = true
                true
            },
        )
        assertFalse(invoked)
    }

    @Test
    fun isFailureAnd_tests_the_failure_exception() {
        val failure = Result.failure<Int>(IllegalStateException("boom"))
        assertTrue(failure.isFailureAnd { it is IllegalStateException })
        assertFalse(failure.isFailureAnd { it is IllegalArgumentException })

        var invoked = false
        assertFalse(
            Result.success(1).isFailureAnd {
                invoked = true
                true
            },
        )
        assertFalse(invoked)
    }
}
