/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class RetryTest {

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
                awaitCancellation()
            }
        }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, attempts)
    }

    @Test
    fun retryWithBackoffRetriesTimeoutsInsideTheBlock() = runTest {
        var attempts = 0
        val value = retryWithBackoff(times = 3, initialDelay = 10.milliseconds) { attempt ->
            attempts++
            withTimeout(50.milliseconds) {
                if (attempt < 3) awaitCancellation()
                "ok"
            }
        }
        assertEquals("ok", value)
        assertEquals(3, attempts)
    }

    @Test
    fun retryWithBackoffRethrowsTheLastTimeoutUnchanged() = runTest {
        val failure = assertFailsWith<TimeoutCancellationException> {
            retryWithBackoff<Unit>(times = 2, initialDelay = 10.milliseconds) {
                withTimeout(50.milliseconds) { awaitCancellation() }
            }
        }
        assertIs<TimeoutCancellationException>(failure)
        assertTrue(currentCoroutineContext().isActive)
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
            runCatchingCancellable<Unit> { awaitCancellation() }
            reached = true
        }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(reached)
    }

    @Test
    fun runCatchingCancellableCapturesCancellationThatIsNotTheCallers() = runTest {
        val timedOut = runCatchingCancellable<Unit> { withTimeout(10.milliseconds) { awaitCancellation() } }
        assertIs<TimeoutCancellationException>(timedOut.exceptionOrNull())

        val cancelledElsewhere = CompletableDeferred<Int>().apply { cancel() }
        val awaited = runCatchingCancellable { cancelledElsewhere.await() }
        assertIs<CancellationException>(awaited.exceptionOrNull())

        val thrown = runCatchingCancellable<Unit> { throw CancellationException("not ours") }
        assertIs<CancellationException>(thrown.exceptionOrNull())
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun runCatchingCancellableNeverReturnsToACancelledCaller() = runTest {
        var reached = false
        val job = launch {
            runCatchingCancellable<Unit> {
                try {
                    awaitCancellation()
                } finally {
                    throw IllegalStateException("cleanup failed")
                }
            }
            reached = true
        }
        runCurrent()
        job.cancel()
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
            "abc".runCatchingCancellable<String, Int> { awaitCancellation() }
            reached = true
        }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(reached)
    }

    @Test
    fun runCatchingCancellableWithReceiverCapturesATimeout() = runTest {
        val timedOut = "abc".runCatchingCancellable<String, Int> { withTimeout(10.milliseconds) { awaitCancellation() } }
        assertIs<TimeoutCancellationException>(timedOut.exceptionOrNull())
    }
}
