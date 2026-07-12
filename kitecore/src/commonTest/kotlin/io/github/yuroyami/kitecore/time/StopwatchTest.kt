/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class) // TestScope.currentTime

package io.github.yuroyami.kitecore.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class StopwatchTest {

    @Test
    fun starts_stopped_at_zero() {
        val watch = Stopwatch(TestTimeSource())
        assertFalse(watch.isRunning)
        assertEquals(Duration.ZERO, watch.elapsed)
    }

    @Test
    fun elapsed_updates_live_while_running() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        assertTrue(watch.isRunning)
        ts += 100.milliseconds
        assertEquals(100.milliseconds, watch.elapsed)
        ts += 25.milliseconds
        assertEquals(125.milliseconds, watch.elapsed)
    }

    @Test
    fun stop_freezes_elapsed_and_accumulates_across_cycles() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        ts += 100.milliseconds
        watch.stop()
        assertFalse(watch.isRunning)
        ts += 50.milliseconds
        assertEquals(100.milliseconds, watch.elapsed)
        watch.start()
        ts += 25.milliseconds
        watch.stop()
        assertEquals(125.milliseconds, watch.elapsed)
    }

    @Test
    fun start_while_running_keeps_the_current_segment() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        ts += 10.milliseconds
        watch.start()
        ts += 10.milliseconds
        assertEquals(20.milliseconds, watch.elapsed)
    }

    @Test
    fun stop_when_already_stopped_changes_nothing() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        ts += 30.milliseconds
        watch.stop()
        watch.stop()
        ts += 30.milliseconds
        assertEquals(30.milliseconds, watch.elapsed)
        assertFalse(watch.isRunning)
    }

    @Test
    fun reset_clears_and_stops() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        ts += 80.milliseconds
        watch.reset()
        assertFalse(watch.isRunning)
        assertEquals(Duration.ZERO, watch.elapsed)
        ts += 80.milliseconds
        assertEquals(Duration.ZERO, watch.elapsed)
    }

    @Test
    fun restart_clears_and_runs() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        ts += 50.milliseconds
        watch.restart()
        assertTrue(watch.isRunning)
        assertEquals(Duration.ZERO, watch.elapsed)
        ts += 30.milliseconds
        assertEquals(30.milliseconds, watch.elapsed)
    }

    @Test
    fun measure_times_the_block_and_returns_its_result() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        val result = watch.measure {
            ts += 40.milliseconds
            "value"
        }
        assertEquals("value", result)
        assertEquals(40.milliseconds, watch.elapsed)
        assertFalse(watch.isRunning)
    }

    @Test
    fun measure_stops_the_watch_when_the_block_throws() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        assertFailsWith<IllegalStateException> {
            watch.measure {
                ts += 15.milliseconds
                throw IllegalStateException("boom")
            }
        }
        assertFalse(watch.isRunning)
        assertEquals(15.milliseconds, watch.elapsed)
    }

    @Test
    fun measure_adds_to_a_running_watch_and_stops_it() {
        val ts = TestTimeSource()
        val watch = Stopwatch(ts)
        watch.start()
        ts += 10.milliseconds
        watch.measure { ts += 5.milliseconds }
        assertEquals(15.milliseconds, watch.elapsed)
        assertFalse(watch.isRunning)
    }
}

class DeadlineTest {

    @Test
    fun remaining_counts_down_and_floors_at_zero() {
        val ts = TestTimeSource()
        val deadline = Deadline(200.milliseconds, ts)
        assertEquals(200.milliseconds, deadline.remaining)
        assertFalse(deadline.isExpired)
        ts += 150.milliseconds
        assertEquals(50.milliseconds, deadline.remaining)
        ts += 100.milliseconds
        assertEquals(Duration.ZERO, deadline.remaining)
        assertTrue(deadline.isExpired)
    }

    @Test
    fun timeout_and_elapsed_report_the_raw_values() {
        val ts = TestTimeSource()
        val deadline = Deadline(200.milliseconds, ts)
        assertEquals(200.milliseconds, deadline.timeout)
        ts += 300.milliseconds
        assertEquals(300.milliseconds, deadline.elapsed)
        assertEquals(200.milliseconds, deadline.timeout)
    }

    @Test
    fun progress_grows_from_zero_to_one_and_caps() {
        val ts = TestTimeSource()
        val deadline = Deadline(200.milliseconds, ts)
        assertEquals(0.0, deadline.progress)
        ts += 100.milliseconds
        assertEquals(0.5, deadline.progress)
        ts += 300.milliseconds
        assertEquals(1.0, deadline.progress)
    }

    @Test
    fun non_positive_timeouts_start_expired() {
        val ts = TestTimeSource()
        val zero = Deadline(Duration.ZERO, ts)
        assertTrue(zero.isExpired)
        assertEquals(Duration.ZERO, zero.remaining)
        assertEquals(1.0, zero.progress)
        val negative = Deadline((-5).milliseconds, ts)
        assertTrue(negative.isExpired)
        assertEquals(Duration.ZERO, negative.remaining)
        assertEquals(1.0, negative.progress)
    }

    @Test
    fun check_not_expired_throws_only_after_expiry() {
        val ts = TestTimeSource()
        val deadline = Deadline(100.milliseconds, ts)
        deadline.checkNotExpired()
        ts += 100.milliseconds
        assertFailsWith<IllegalStateException> { deadline.checkNotExpired() }
    }

    @Test
    fun or_null_returns_the_value_when_the_block_beats_the_deadline() = runTest {
        val deadline = Deadline(100.milliseconds, TestTimeSource())
        val value = deadline.orNull {
            delay(50.milliseconds)
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(50, currentTime)
    }

    @Test
    fun or_null_returns_null_when_the_block_runs_past_the_deadline() = runTest {
        val deadline = Deadline(100.milliseconds, TestTimeSource())
        val value = deadline.orNull {
            delay(150.milliseconds)
            "late"
        }
        assertNull(value)
        assertEquals(100, currentTime)
    }

    @Test
    fun or_null_skips_the_block_when_already_expired() = runTest {
        val ts = TestTimeSource()
        val deadline = Deadline(100.milliseconds, ts)
        ts += 100.milliseconds
        var ran = false
        val value = deadline.orNull { ran = true }
        assertNull(value)
        assertFalse(ran)
    }

    @Test
    fun await_suspends_for_the_remaining_time() = runTest {
        val ts = TestTimeSource()
        val deadline = Deadline(250.milliseconds, ts)
        deadline.await()
        assertEquals(250, currentTime)
        ts += 300.milliseconds
        deadline.await() // expired, so no additional delay
        assertEquals(250, currentTime)
    }

    @Test
    fun await_waits_less_after_time_has_passed() = runTest {
        val ts = TestTimeSource()
        val deadline = Deadline(250.milliseconds, ts)
        ts += 100.milliseconds
        deadline.await()
        assertEquals(150, currentTime)
    }
}
