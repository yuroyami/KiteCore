/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RetryTest {

    @Test
    fun retryReturnsFirstSuccessWithoutExtraAttempts() = runTest {
        var attempts = 0
        val value = retry(3) { attempt ->
            attempts++
            assertEquals(attempts, attempt)
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(1, attempts)
        assertEquals(0, currentTime)
    }

    @Test
    fun retryRetriesWithDelayUntilSuccess() = runTest {
        var attempts = 0
        val value = retry(times = 3, delayBetween = 100.milliseconds) { attempt ->
            attempts++
            if (attempt < 3) throw IllegalStateException("attempt $attempt")
            "done"
        }
        assertEquals("done", value)
        assertEquals(3, attempts)
        assertEquals(200, currentTime)
    }

    @Test
    fun retryRethrowsTheLastFailure() = runTest {
        var attempts = 0
        val failure = assertFailsWith<IllegalStateException> {
            retry<Unit>(2) { attempt ->
                attempts++
                throw IllegalStateException("failure $attempt")
            }
        }
        assertEquals("failure 2", failure.message)
        assertEquals(2, attempts)
    }

    @Test
    fun retryNeverRetriesCancellation() = runTest {
        var attempts = 0
        val job = launch {
            retry<Unit>(times = 5, delayBetween = 10.milliseconds) {
                attempts++
                throw CancellationException("stop")
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, attempts)
    }

    @Test
    fun retryValidatesArguments() = runTest {
        assertFailsWith<IllegalArgumentException> { retry<Unit>(0) { } }
        assertFailsWith<IllegalArgumentException> {
            retry<Unit>(1, delayBetween = (-1).milliseconds) { }
        }
    }

    @Test
    fun retryWithBackoffGrowsDelaysAndCapsAtMaxDelay() = runTest {
        var attempts = 0
        val value = retryWithBackoff(
            times = 5,
            initialDelay = 100.milliseconds,
            factor = 2.0,
            maxDelay = 300.milliseconds,
        ) { attempt ->
            attempts++
            if (attempt < 5) throw IllegalStateException("attempt $attempt")
            "done"
        }
        assertEquals("done", value)
        assertEquals(5, attempts)
        // Delays: 100 + 200 + 300 (capped) + 300 (capped) = 900.
        assertEquals(900, currentTime)
    }

    @Test
    fun retryWithBackoffStopsWhenRetryIfRejectsTheFailure() = runTest {
        var attempts = 0
        val failure = assertFailsWith<IllegalArgumentException> {
            retryWithBackoff<Unit>(
                times = 5,
                initialDelay = 10.milliseconds,
                retryIf = { it is IllegalStateException },
            ) { attempt ->
                attempts++
                if (attempt == 2) throw IllegalArgumentException("not retryable")
                throw IllegalStateException("retryable")
            }
        }
        assertEquals("not retryable", failure.message)
        assertEquals(2, attempts)
    }

    @Test
    fun retryWithBackoffNeverRetriesCancellation() = runTest {
        var attempts = 0
        val job = launch {
            retryWithBackoff<Unit>(times = 5, initialDelay = 10.milliseconds) {
                attempts++
                throw CancellationException("stop")
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, attempts)
    }

    @Test
    fun retryWithBackoffValidatesArguments() = runTest {
        assertFailsWith<IllegalArgumentException> {
            retryWithBackoff<Unit>(times = 0, initialDelay = 1.milliseconds) { }
        }
        assertFailsWith<IllegalArgumentException> {
            retryWithBackoff<Unit>(times = 1, initialDelay = (-1).milliseconds) { }
        }
        assertFailsWith<IllegalArgumentException> {
            retryWithBackoff<Unit>(times = 1, initialDelay = 1.milliseconds, factor = 0.0) { }
        }
        assertFailsWith<IllegalArgumentException> {
            retryWithBackoff<Unit>(times = 1, initialDelay = 1.milliseconds, maxDelay = (-1).milliseconds) { }
        }
    }

    @Test
    fun retryForeverRetriesUntilSuccess() = runTest {
        var attempts = 0
        val value = retryForever(delayBetween = 10.milliseconds) { attempt ->
            attempts++
            if (attempt < 4) throw IllegalStateException("attempt $attempt")
            "done"
        }
        assertEquals("done", value)
        assertEquals(4, attempts)
        assertEquals(30, currentTime)
    }

    @Test
    fun retryForeverStopsWhenRetryIfRejectsTheFailure() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            retryForever<Unit>(retryIf = { it is IllegalStateException }) {
                throw IllegalArgumentException("fatal")
            }
        }
        assertEquals("fatal", failure.message)
    }

    @Test
    fun retryForeverStopsOnCancellation() = runTest {
        var attempts = 0
        val job = launch {
            retryForever<Unit>(delayBetween = 10.milliseconds) {
                attempts++
                throw IllegalStateException("again")
            }
        }
        advanceTimeBy(55)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(6, attempts)
    }

    @Test
    fun runCatchingCancellableCapturesSuccessAndFailure() = runTest {
        val success = runCatchingCancellable { "value" }
        assertEquals("value", success.getOrNull())

        val failure = runCatchingCancellable<Unit> { throw IllegalStateException("boom") }
        assertIs<IllegalStateException>(failure.exceptionOrNull())
    }

    @Test
    fun runCatchingCancellableRethrowsCancellation() = runTest {
        var reached = false
        val job = launch {
            runCatchingCancellable<Unit> { throw CancellationException("stop") }
            reached = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(reached)
    }

    @Test
    fun runCatchingCancellableWithReceiverCapturesSuccessAndFailure() = runTest {
        val success = "abc".runCatchingCancellable { length }
        assertEquals(3, success.getOrNull())

        val failure = "abc".runCatchingCancellable<String, Int> { throw IllegalStateException("boom") }
        assertIs<IllegalStateException>(failure.exceptionOrNull())
    }

    @Test
    fun runCatchingCancellableWithReceiverRethrowsCancellation() = runTest {
        var reached = false
        val job = launch {
            "abc".runCatchingCancellable<String, Int> { throw CancellationException("stop") }
            reached = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(reached)
    }

    @Test
    fun withTimeoutOrDefaultReturnsTheBlockValueInTime() = runTest {
        val value = withTimeoutOrDefault(100.milliseconds, "default") {
            delay(50)
            "value"
        }
        assertEquals("value", value)
        assertEquals(50, currentTime)
    }

    @Test
    fun withTimeoutOrDefaultReturnsDefaultOnTimeout() = runTest {
        val value = withTimeoutOrDefault(100.milliseconds, "default") {
            delay(200)
            "value"
        }
        assertEquals("default", value)
        assertEquals(100, currentTime)
    }

    @Test
    fun withTimeoutOrDefaultReturnsDefaultForNonPositiveTimeout() = runTest {
        var ran = false
        val value = withTimeoutOrDefault(Duration.ZERO, "default") {
            ran = true
            "value"
        }
        assertEquals("default", value)
        assertFalse(ran)
    }

    @Test
    fun withTimeoutOrDefaultPropagatesCallerCancellation() = runTest {
        var completed = false
        val job = launch {
            withTimeoutOrDefault(1000.milliseconds, "default") {
                delay(500)
                "value"
            }
            completed = true
        }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(completed)
    }

    @Test
    fun raceOfReturnsTheFastestValueAndCancelsLosers() = runTest {
        var loserCancelled = false
        val winner = raceOf(
            {
                try {
                    delay(100)
                    "slow"
                } catch (cause: CancellationException) {
                    loserCancelled = true
                    throw cause
                }
            },
            {
                delay(10)
                "fast"
            },
        )
        assertEquals("fast", winner)
        assertEquals(10, currentTime)
        assertTrue(loserCancelled)
    }

    @Test
    fun raceOfRethrowsTheFirstCompletionWhenItFails() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            raceOf(
                {
                    delay(10)
                    throw IllegalStateException("fast failure")
                },
                {
                    delay(100)
                    "slow"
                },
            )
        }
        assertEquals("fast failure", failure.message)
    }

    @Test
    fun raceOfRejectsEmptyInput() = runTest {
        assertFailsWith<IllegalArgumentException> { raceOf<Unit>() }
    }

    @Test
    fun firstSuccessOfSkipsEarlierFailures() = runTest {
        val value = firstSuccessOf(
            {
                delay(10)
                throw IllegalStateException("early failure")
            },
            {
                delay(100)
                "late success"
            },
        )
        assertEquals("late success", value)
        assertEquals(100, currentTime)
    }

    @Test
    fun firstSuccessOfCancelsLosersOnceAWinnerExists() = runTest {
        var loserCancelled = false
        val value = firstSuccessOf(
            {
                delay(10)
                "winner"
            },
            {
                try {
                    delay(1000)
                    "slow"
                } catch (cause: CancellationException) {
                    loserCancelled = true
                    throw cause
                }
            },
        )
        assertEquals("winner", value)
        assertTrue(loserCancelled)
    }

    @Test
    fun firstSuccessOfRethrowsTheLastFailureWhenAllFail() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            firstSuccessOf<String>(
                {
                    delay(10)
                    throw IllegalStateException("first")
                },
                {
                    delay(20)
                    throw IllegalStateException("second")
                },
            )
        }
        assertEquals("second", failure.message)
    }

    @Test
    fun firstSuccessOfRejectsEmptyInput() = runTest {
        assertFailsWith<IllegalArgumentException> { firstSuccessOf<Unit>() }
    }
}
