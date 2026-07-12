/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class) // TestScope.currentTime, advanceTimeBy, runCurrent

package io.github.yuroyami.kitecore.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RateLimiterTest {

    @Test
    fun rejects_invalid_configuration() {
        assertFailsWith<IllegalArgumentException> { RateLimiter(0, 100.milliseconds) }
        assertFailsWith<IllegalArgumentException> { RateLimiter(1, Duration.ZERO) }
    }

    @Test
    fun exposes_its_configuration() {
        val limiter = RateLimiter(3, 250.milliseconds, TestTimeSource())
        assertEquals(3, limiter.permits)
        assertEquals(250.milliseconds, limiter.per)
    }

    @Test
    fun try_acquire_consumes_permits_until_exhausted() {
        val ts = TestTimeSource()
        val limiter = RateLimiter(2, 100.milliseconds, ts)
        assertEquals(2, limiter.availablePermits)
        assertTrue(limiter.tryAcquire())
        assertEquals(1, limiter.availablePermits)
        assertTrue(limiter.tryAcquire())
        assertEquals(0, limiter.availablePermits)
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun the_window_slides_rather_than_resets() {
        val ts = TestTimeSource()
        val limiter = RateLimiter(2, 100.milliseconds, ts)
        assertTrue(limiter.tryAcquire()) // taken at t = 0
        ts += 60.milliseconds
        assertTrue(limiter.tryAcquire()) // taken at t = 60
        assertFalse(limiter.tryAcquire())
        ts += 40.milliseconds // t = 100: the first mark leaves the window
        assertTrue(limiter.tryAcquire())
        assertEquals(0, limiter.availablePermits)
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun acquire_returns_without_waiting_when_a_permit_is_free() = runTest {
        val limiter = RateLimiter(1, 100.milliseconds, TestTimeSource())
        limiter.acquire()
        assertEquals(0, currentTime)
        assertEquals(0, limiter.availablePermits)
    }

    @Test
    fun acquire_suspends_until_a_permit_frees() = runTest {
        val ts = TestTimeSource()
        val limiter = RateLimiter(1, 100.milliseconds, ts)
        limiter.acquire()
        var acquired = false
        val job = launch {
            limiter.acquire()
            acquired = true
        }
        runCurrent()
        assertFalse(acquired)
        ts += 100.milliseconds
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertTrue(acquired)
        assertTrue(job.isCompleted)
        assertEquals(0, limiter.availablePermits)
    }

    @Test
    fun try_acquire_reports_busy_while_an_acquirer_waits() = runTest {
        val ts = TestTimeSource()
        val limiter = RateLimiter(1, 100.milliseconds, ts)
        limiter.acquire()
        val job = launch { limiter.acquire() }
        runCurrent()
        assertFalse(limiter.tryAcquire()) // the waiter holds the mutex
        ts += 100.milliseconds
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertTrue(job.isCompleted)
        assertFalse(limiter.tryAcquire()) // the waiter took the freed permit
    }

    @Test
    fun with_permit_runs_the_block_and_consumes_a_permit() = runTest {
        val limiter = RateLimiter(2, 100.milliseconds, TestTimeSource())
        val result = limiter.withPermit { "value" }
        assertEquals("value", result)
        assertEquals(1, limiter.availablePermits)
    }
}

class TimingHelpersTest {

    @Test
    fun with_minimum_duration_pads_a_fast_block() = runTest {
        val ts = TestTimeSource()
        val result = withMinimumDuration(1.seconds, ts) {
            ts += 300.milliseconds
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(700, currentTime)
    }

    @Test
    fun with_minimum_duration_adds_nothing_to_a_slow_block() = runTest {
        val ts = TestTimeSource()
        val result = withMinimumDuration(1.seconds, ts) {
            ts += 2.seconds
            42
        }
        assertEquals(42, result)
        assertEquals(0, currentTime)
    }

    @Test
    fun with_minimum_duration_rethrows_without_padding() = runTest {
        val ts = TestTimeSource()
        assertFailsWith<IllegalStateException> {
            withMinimumDuration(1.seconds, ts) { throw IllegalStateException("boom") }
        }
        assertEquals(0, currentTime)
    }

    @Test
    fun poll_until_returns_true_at_the_immediate_check() = runTest {
        var calls = 0
        val result = pollUntil(100.milliseconds, 1.seconds) {
            calls++
            true
        }
        assertTrue(result)
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun poll_until_polls_at_the_interval_until_true() = runTest {
        var calls = 0
        val result = pollUntil(100.milliseconds, 1.seconds) { ++calls >= 4 }
        assertTrue(result)
        assertEquals(4, calls)
        assertEquals(300, currentTime)
    }

    @Test
    fun poll_until_gives_up_at_the_timeout() = runTest {
        val result = pollUntil(100.milliseconds, 250.milliseconds) { false }
        assertFalse(result)
        assertEquals(250, currentTime)
    }

    @Test
    fun poll_until_with_zero_timeout_only_checks_once() = runTest {
        assertTrue(pollUntil(100.milliseconds, Duration.ZERO) { true })
        var calls = 0
        assertFalse(
            pollUntil(100.milliseconds, Duration.ZERO) {
                calls++
                false
            },
        )
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun poll_until_rejects_a_non_positive_interval() = runTest {
        assertFailsWith<IllegalArgumentException> {
            pollUntil(Duration.ZERO, 1.seconds) { true }
        }
    }
}

class BackoffSequenceTest {

    @Test
    fun doubles_from_the_initial_delay_by_default() {
        val delays = backoffSequence(100.milliseconds).take(5).toList()
        assertEquals(
            listOf(100.milliseconds, 200.milliseconds, 400.milliseconds, 800.milliseconds, 1600.milliseconds),
            delays,
        )
    }

    @Test
    fun caps_every_element_at_max_delay() {
        val delays = backoffSequence(100.milliseconds, maxDelay = 500.milliseconds).take(5).toList()
        assertEquals(
            listOf(100.milliseconds, 200.milliseconds, 400.milliseconds, 500.milliseconds, 500.milliseconds),
            delays,
        )
    }

    @Test
    fun caps_an_initial_delay_above_max() {
        assertEquals(500.milliseconds, backoffSequence(1.seconds, maxDelay = 500.milliseconds).first())
    }

    @Test
    fun a_factor_of_one_repeats_the_initial_delay() {
        val delays = backoffSequence(100.milliseconds, factor = 1.0).take(3).toList()
        assertEquals(listOf(100.milliseconds, 100.milliseconds, 100.milliseconds), delays)
    }

    @Test
    fun saturates_at_infinite_without_a_cap() {
        val delays = backoffSequence(1.days, factor = 1e9).take(3).toList()
        assertEquals(1.days, delays[0])
        assertTrue(delays[1].isFinite())
        assertEquals(Duration.INFINITE, delays[2])
    }

    @Test
    fun can_be_iterated_more_than_once() {
        val sequence = backoffSequence(100.milliseconds)
        assertEquals(sequence.take(3).toList(), sequence.take(3).toList())
    }

    @Test
    fun rejects_invalid_arguments() {
        assertFailsWith<IllegalArgumentException> { backoffSequence((-1).milliseconds) }
        assertFailsWith<IllegalArgumentException> { backoffSequence(100.milliseconds, factor = 0.9) }
        assertFailsWith<IllegalArgumentException> { backoffSequence(100.milliseconds, maxDelay = (-1).milliseconds) }
    }
}

