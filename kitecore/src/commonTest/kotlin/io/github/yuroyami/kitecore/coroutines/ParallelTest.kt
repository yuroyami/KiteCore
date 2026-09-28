/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class ParallelTest {

    @Test
    fun parallelMapPreservesInputOrder() = runTest {
        val input = (1..20).toList()
        // Later elements finish first, so completion order is the reverse of input order.
        val output = input.parallelMap { element ->
            delay((21 - element).milliseconds)
            element * 2
        }
        assertEquals(input.map { it * 2 }, output)
    }

    @Test
    fun parallelMapRunsElementsConcurrently() = runTest {
        val start = currentTime
        (1..4).toList().parallelMap { element ->
            delay(100)
            element
        }
        assertEquals(100, currentTime - start)
    }

    @Test
    fun parallelMapHonorsTheConcurrencyLimit() = runTest {
        var active = 0
        var maxActive = 0
        val start = currentTime
        (1..4).toList().parallelMap(concurrency = 2) { element ->
            active++
            maxActive = maxOf(maxActive, active)
            delay(100)
            active--
            element
        }
        assertEquals(200, currentTime - start)
        assertEquals(2, maxActive)
    }

    @Test
    fun parallelMapFailureCancelsSiblingsAndRethrows() = runTest {
        var siblingCancelled = false
        val failure = assertFailsWith<IllegalStateException> {
            listOf(1, 2).parallelMap { element ->
                if (element == 1) {
                    delay(10)
                    throw IllegalStateException("boom")
                }
                try {
                    delay(1000)
                    element
                } catch (cause: CancellationException) {
                    siblingCancelled = true
                    throw cause
                }
            }
        }
        assertEquals("boom", failure.message)
        assertTrue(siblingCancelled)
    }

    @Test
    fun parallelMapValidatesConcurrency() = runTest {
        assertFailsWith<IllegalArgumentException> {
            listOf(1).parallelMap(concurrency = 0) { it }
        }
    }

    @Test
    fun parallelForEachVisitsEveryElementConcurrently() = runTest {
        val visited = mutableSetOf<Int>()
        val start = currentTime
        (1..5).toList().parallelForEach { element ->
            delay(100)
            visited += element
        }
        assertEquals(setOf(1, 2, 3, 4, 5), visited)
        assertEquals(100, currentTime - start)
    }

    @Test
    fun parallelForEachHonorsTheConcurrencyLimit() = runTest {
        val start = currentTime
        (1..4).toList().parallelForEach(concurrency = 1) {
            delay(50)
        }
        assertEquals(200, currentTime - start)
    }
}
