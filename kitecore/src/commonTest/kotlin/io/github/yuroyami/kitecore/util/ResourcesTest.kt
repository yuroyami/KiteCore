/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourcesTest {

    /** An [Error] subclass used to verify that non-Exception throwables propagate. */
    private class TestError : Error("test error")

    /** Records its close into [log] and optionally throws [failure]. */
    private class Recorder(
        private val name: String,
        private val log: MutableList<String>,
        private val failure: Throwable? = null,
    ) : AutoCloseable {
        override fun close() {
            log.add(name)
            failure?.let { throw it }
        }
    }

    @Test
    fun useAll_returns_the_block_value_and_closes_in_reverse_order() {
        val log = mutableListOf<String>()
        val a = Recorder("a", log)
        val b = Recorder("b", log)
        val c = Recorder("c", log)

        val result = useAll(a, b, c) { "payload" }

        assertEquals("payload", result)
        assertEquals(listOf("c", "b", "a"), log)
    }

    @Test
    fun useAll_works_with_zero_resources() {
        assertEquals(42, useAll { 42 })
    }

    @Test
    fun useAll_suppresses_close_failures_onto_the_block_failure() {
        val log = mutableListOf<String>()
        val closeBoom = IllegalArgumentException("close failed")
        val a = Recorder("a", log)
        val b = Recorder("b", log, failure = closeBoom)

        val thrown = assertFailsWith<IllegalStateException> {
            useAll(a, b) { throw IllegalStateException("block failed") }
        }

        assertEquals("block failed", thrown.message)
        assertEquals(listOf("b", "a"), log) // every resource still closed
        assertTrue(closeBoom in thrown.suppressedExceptions)
    }

    @Test
    fun useAll_throws_the_first_close_failure_after_closing_everything() {
        val log = mutableListOf<String>()
        val firstCloseBoom = IllegalStateException("first close failure")
        val laterCloseBoom = IllegalArgumentException("later close failure")
        // Close order is c, b, a: c fails first, a fails afterwards.
        val a = Recorder("a", log, failure = laterCloseBoom)
        val b = Recorder("b", log)
        val c = Recorder("c", log, failure = firstCloseBoom)

        val thrown = assertFailsWith<IllegalStateException> {
            useAll(a, b, c) { "ignored result" }
        }

        assertSame(firstCloseBoom, thrown)
        assertEquals(listOf("c", "b", "a"), log) // every resource still closed
        assertTrue(laterCloseBoom in thrown.suppressedExceptions)
    }

    @Test
    fun useAll_on_an_iterable_closes_in_reverse_iteration_order() {
        val log = mutableListOf<String>()
        val resources: List<AutoCloseable> = listOf(
            Recorder("a", log),
            Recorder("b", log),
        )

        val result = resources.useAll { "payload" }

        assertEquals("payload", result)
        assertEquals(listOf("b", "a"), log)
    }

    @Test
    fun closeQuietly_swallows_exceptions_from_close() {
        val log = mutableListOf<String>()
        val failing = Recorder("boom", log, failure = IllegalStateException("close failed"))
        failing.closeQuietly() // must not throw
        assertEquals(listOf("boom"), log)
    }

    @Test
    fun closeQuietly_lets_errors_propagate() {
        val log = mutableListOf<String>()
        val failing = Recorder("fatal", log, failure = TestError())
        assertFailsWith<TestError> { failing.closeQuietly() }
    }
}
