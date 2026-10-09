/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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

    @Test
    fun parallelMapHandlesAnEmptyInputAndMoreWorkersThanElements() = runTest {
        assertEquals(emptyList(), emptyList<Int>().parallelMap(concurrency = 4) { it })
        assertEquals(listOf(2, 4), listOf(1, 2).parallelMap(concurrency = 64) { it * 2 })
        var visited = 0
        emptyList<Int>().parallelForEach(concurrency = 4) { visited++ }
        assertEquals(0, visited)
    }

    @Test
    fun parallelMapProcessesALargeInputWithASmallPool() = runTest {
        val input = (0 until 20_000).toList()
        val output = input.parallelMap(concurrency = 4) { element ->
            if (element % 1_000 == 0) yield()
            element + 1
        }
        assertEquals(input.map { it + 1 }, output)
    }

    @Test
    fun parallelMapKeepsTheLimitUnderRealThreads() = runTest {
        val active = MutableStateFlow(0)
        val maxActive = MutableStateFlow(0)
        val output = withContext(Dispatchers.Default) {
            (1..200).toList().parallelMap(concurrency = 3) { element ->
                val now = active.updateAndGet { it + 1 }
                maxActive.update { maxOf(it, now) }
                yield()
                active.update { it - 1 }
                element
            }
        }
        assertEquals((1..200).toList(), output)
        assertTrue(maxActive.value in 1..3, "maxActive = ${maxActive.value}")
    }

    @Test
    fun parallelForEachKeepsTheLimitUnderRealThreads() = runTest {
        val active = MutableStateFlow(0)
        val maxActive = MutableStateFlow(0)
        val visited = MutableStateFlow(0)
        withContext(Dispatchers.Default) {
            (1..200).toList().parallelForEach(concurrency = 3) {
                val now = active.updateAndGet { it + 1 }
                maxActive.update { maxOf(it, now) }
                yield()
                active.update { it - 1 }
                visited.update { it + 1 }
            }
        }
        assertEquals(200, visited.value)
        assertTrue(maxActive.value in 1..3, "maxActive = ${maxActive.value}")
    }
}
