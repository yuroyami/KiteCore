/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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
    fun parallelMapIndexedPassesTheRightIndices() = runTest {
        val output = listOf("a", "b", "c").parallelMapIndexed { index, element ->
            delay((3 - index).milliseconds)
            "$index$element"
        }
        assertEquals(listOf("0a", "1b", "2c"), output)
    }

    @Test
    fun parallelMapNotNullDropsNullResults() = runTest {
        val output = (1..6).toList().parallelMapNotNull { element ->
            delay(1)
            if (element % 2 == 0) element else null
        }
        assertEquals(listOf(2, 4, 6), output)
    }

    @Test
    fun parallelMapIndexedNotNullDropsNullResults() = runTest {
        val output = listOf("keep", "drop", "keep").parallelMapIndexedNotNull { index, element ->
            if (element == "keep") index else null
        }
        assertEquals(listOf(0, 2), output)
    }

    @Test
    fun parallelFlatMapFlattensInInputOrder() = runTest {
        val output = listOf(1, 2, 3).parallelFlatMap { element ->
            delay((4 - element).milliseconds)
            listOf(element, element * 10)
        }
        assertEquals(listOf(1, 10, 2, 20, 3, 30), output)
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
    fun parallelForEachIndexedPassesTheRightIndices() = runTest {
        val seen = mutableMapOf<Int, String>()
        listOf("a", "b", "c").parallelForEachIndexed { index, element ->
            delay(1)
            seen[index] = element
        }
        assertEquals(mapOf(0 to "a", 1 to "b", 2 to "c"), seen)
    }

    @Test
    fun parallelFilterKeepsMatchesInInputOrder() = runTest {
        val output = (1..10).toList().parallelFilter { element ->
            delay((element % 3).milliseconds)
            element % 2 == 0
        }
        assertEquals(listOf(2, 4, 6, 8, 10), output)
    }

    @Test
    fun parallelFilterNotKeepsNonMatchesInInputOrder() = runTest {
        val output = (1..10).toList().parallelFilterNot { element ->
            delay(1)
            element % 2 == 0
        }
        assertEquals(listOf(1, 3, 5, 7, 9), output)
    }

    @Test
    fun awaitAllSettledCollectsEveryOutcomeInOrder() = runTest {
        supervisorScope {
            val ok = async {
                delay(10)
                1
            }
            val bad = async<Int> {
                delay(20)
                throw IllegalStateException("boom")
            }
            val results = listOf(ok, bad).awaitAllSettled()
            assertEquals(1, results[0].getOrThrow())
            assertIs<IllegalStateException>(results[1].exceptionOrNull())
        }
    }

    @Test
    fun awaitAllSettledRecordsACancelledDeferredAsFailure() = runTest {
        val cancelled = CompletableDeferred<Int>()
        cancelled.cancel()
        val done = CompletableDeferred<Int>()
        done.complete(5)
        val results = listOf(cancelled, done).awaitAllSettled()
        assertIs<CancellationException>(results[0].exceptionOrNull())
        assertEquals(5, results[1].getOrThrow())
    }

    @Test
    fun awaitAllSettledPropagatesCallerCancellation() = runTest {
        val never = CompletableDeferred<Int>()
        var reached = false
        val caller = launch {
            listOf(never).awaitAllSettled()
            reached = true
        }
        runCurrent()
        caller.cancel()
        caller.join()
        assertTrue(caller.isCancelled)
        assertFalse(reached)
    }

    @Test
    fun awaitOrNullReturnsTheValueInTime() = runTest {
        val deferred = CompletableDeferred<Int>()
        launch {
            delay(50)
            deferred.complete(9)
        }
        assertEquals(9, deferred.awaitOrNull(100.milliseconds))
    }

    @Test
    fun awaitOrNullReturnsNullOnTimeoutWithoutCancellingTheDeferred() = runTest {
        val slow = CompletableDeferred<Int>()
        launch {
            delay(200)
            slow.complete(1)
        }
        assertNull(slow.awaitOrNull(100.milliseconds))
        assertFalse(slow.isCancelled)
        assertEquals(1, slow.awaitOrNull(200.milliseconds))
    }

    @Test
    fun awaitOrNullRethrowsTheDeferredFailure() = runTest {
        val failed = CompletableDeferred<Int>()
        failed.completeExceptionally(IllegalStateException("boom"))
        val failure = assertFailsWith<IllegalStateException> {
            failed.awaitOrNull(100.milliseconds)
        }
        assertEquals("boom", failure.message)
    }

    @Test
    fun awaitOrDefaultReturnsTheValueInTimeAndDefaultOnTimeout() = runTest {
        val fast = CompletableDeferred<Int>()
        fast.complete(3)
        assertEquals(3, fast.awaitOrDefault(100.milliseconds, -1))

        val never = CompletableDeferred<Int>()
        assertEquals(-1, never.awaitOrDefault(100.milliseconds, -1))
    }

    @Test
    fun launchPeriodicRunsOnScheduleUntilCancelled() = runTest {
        val ticks = mutableListOf<Long>()
        val job = launchPeriodic(interval = 100.milliseconds, initialDelay = 50.milliseconds) {
            ticks += currentTime
        }
        advanceTimeBy(400)
        job.cancel()
        job.join()
        assertEquals(listOf(50L, 150L, 250L, 350L), ticks)
    }

    @Test
    fun launchPeriodicValidatesArguments() = runTest {
        assertFailsWith<IllegalArgumentException> {
            launchPeriodic(interval = 0.milliseconds) { }
        }
        assertFailsWith<IllegalArgumentException> {
            launchPeriodic(interval = 10.milliseconds, initialDelay = (-1).milliseconds) { }
        }
    }

    @Test
    fun launchCatchingReportsFailuresToOnErrorAndCompletesNormally() = runTest {
        var captured: Throwable? = null
        val job = launchCatching(onError = { captured = it }) {
            throw IllegalStateException("boom")
        }
        job.join()
        assertFalse(job.isCancelled)
        assertIs<IllegalStateException>(captured)
    }

    @Test
    fun launchCatchingRunsTheBlockWhenNothingFails() = runTest {
        var captured: Throwable? = null
        var ran = false
        val job = launchCatching(onError = { captured = it }) {
            delay(10)
            ran = true
        }
        job.join()
        assertTrue(ran)
        assertNull(captured)
    }

    @Test
    fun launchCatchingNeverReportsCancellationToOnError() = runTest {
        var captured: Throwable? = null
        val job = launchCatching(onError = { captured = it }) {
            delay(1000)
        }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertNull(captured)
    }

    @Test
    fun launchDelayedRunsAfterTheDelayAndSkipsOnCancel() = runTest {
        var ran = false
        val job = launchDelayed(100.milliseconds) { ran = true }
        advanceTimeBy(101)
        runCurrent()
        assertTrue(ran)
        assertTrue(job.isCompleted)
        var second = false
        val cancelled = launchDelayed(100.milliseconds) { second = true }
        advanceTimeBy(50)
        cancelled.cancel()
        advanceTimeBy(100)
        runCurrent()
        assertFalse(second)
    }
}
