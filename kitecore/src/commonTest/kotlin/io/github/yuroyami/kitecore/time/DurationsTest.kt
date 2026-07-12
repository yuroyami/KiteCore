/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationsTest {

    @Test
    fun format_hms_omits_leading_zero_units() {
        assertEquals("0s", Duration.ZERO.formatHms())
        assertEquals("5s", 5.seconds.formatHms())
        assertEquals("1m 5s", 65.seconds.formatHms())
        assertEquals("1h 0m 5s", (1.hours + 5.seconds).formatHms())
        assertEquals("1h 23m 45s", (1.hours + 23.minutes + 45.seconds).formatHms())
    }

    @Test
    fun format_hms_handles_large_truncated_negative_and_infinite_values() {
        assertEquals("25h 0m 0s", 25.hours.formatHms())
        assertEquals("1s", 1500.milliseconds.formatHms())
        assertEquals("-1m 5s", (-(65.seconds)).formatHms())
        assertEquals("0s", (-500).milliseconds.formatHms())
        assertEquals("Infinity", Duration.INFINITE.formatHms())
    }

    @Test
    fun format_clock_pads_and_drops_the_hour_field_under_an_hour() {
        assertEquals("00:00", Duration.ZERO.formatClock())
        assertEquals("00:05", 5.seconds.formatClock())
        assertEquals("23:45", (23.minutes + 45.seconds).formatClock())
        assertEquals("59:59", (59.minutes + 59.seconds + 900.milliseconds).formatClock())
        assertEquals("01:23:45", (1.hours + 23.minutes + 45.seconds).formatClock())
    }

    @Test
    fun format_clock_handles_large_negative_and_infinite_values() {
        assertEquals("100:00:00", 100.hours.formatClock())
        assertEquals("-01:00:00", (-(1.hours)).formatClock())
        assertEquals("00:00", (-500).milliseconds.formatClock())
        assertEquals("Infinity", Duration.INFINITE.formatClock())
    }

    @Test
    fun human_readable_takes_the_most_significant_parts() {
        assertEquals("1h 23m", (1.hours + 23.minutes).humanReadable())
        assertEquals("1d 1h", 25.hours.humanReadable())
        assertEquals("1d 2h", (1.days + 2.hours + 3.minutes).humanReadable())
        assertEquals("1d 2h 3m", (1.days + 2.hours + 3.minutes).humanReadable(3))
        assertEquals("1h", 90.minutes.humanReadable(1))
    }

    @Test
    fun human_readable_skips_zero_parts_and_goes_down_to_milliseconds() {
        assertEquals("1d 5m", (24.hours + 5.minutes).humanReadable())
        assertEquals("1s 500ms", 1500.milliseconds.humanReadable())
        assertEquals("0s", Duration.ZERO.humanReadable())
        assertEquals("0s", 500.microseconds.humanReadable())
        assertEquals("-1h 30m", (-(90.minutes)).humanReadable())
        assertEquals("Infinity", Duration.INFINITE.humanReadable())
    }

    @Test
    fun human_readable_rejects_a_non_positive_part_count() {
        assertFailsWith<IllegalArgumentException> { 1.seconds.humanReadable(0) }
    }

    @Test
    fun round_to_seconds_rounds_half_away_from_zero() {
        assertEquals(1.seconds, 1499.milliseconds.roundToSeconds())
        assertEquals(2.seconds, 1500.milliseconds.roundToSeconds())
        assertEquals(Duration.ZERO, 499.milliseconds.roundToSeconds())
        assertEquals((-2).seconds, (-1500).milliseconds.roundToSeconds())
        assertEquals(Duration.INFINITE, Duration.INFINITE.roundToSeconds())
        assertEquals(-Duration.INFINITE, (-Duration.INFINITE).roundToSeconds())
    }

    @Test
    fun round_to_minutes_rounds_half_away_from_zero() {
        assertEquals(1.minutes, 89.seconds.roundToMinutes())
        assertEquals(2.minutes, 90.seconds.roundToMinutes())
        assertEquals(Duration.ZERO, 29.seconds.roundToMinutes())
        assertEquals((-2).minutes, (-90).seconds.roundToMinutes())
        assertEquals(Duration.INFINITE, Duration.INFINITE.roundToMinutes())
    }

    @Test
    fun round_to_hours_rounds_half_away_from_zero() {
        assertEquals(1.hours, 89.minutes.roundToHours())
        assertEquals(2.hours, 90.minutes.roundToHours())
        assertEquals(Duration.ZERO, 29.minutes.roundToHours())
        assertEquals((-2).hours, (-90).minutes.roundToHours())
        assertEquals(Duration.INFINITE, Duration.INFINITE.roundToHours())
    }

    @Test
    fun parse_clock_reads_both_clock_shapes() {
        assertEquals(1.hours + 23.minutes + 45.seconds, parseClock("01:23:45"))
        assertEquals(23.minutes + 45.seconds, parseClock("23:45"))
        assertEquals(100.hours, parseClock("100:00:00"))
        assertEquals(1.hours + 23.minutes + 45.seconds, parseClock("1:23:45"))
        assertEquals(-(1.hours), parseClock("-01:00:00"))
    }

    @Test
    fun parse_clock_throws_on_invalid_input() {
        assertFailsWith<IllegalArgumentException> { parseClock("nope") }
    }

    @Test
    fun parse_clock_or_null_rejects_malformed_strings() {
        assertNull(parseClockOrNull(""))
        assertNull(parseClockOrNull("5"))
        assertNull(parseClockOrNull("1:2"))
        assertNull(parseClockOrNull("1:23:45:00"))
        assertNull(parseClockOrNull("12:60"))
        assertNull(parseClockOrNull("01:60:00"))
        assertNull(parseClockOrNull("aa:bb"))
        assertNull(parseClockOrNull("12: 5"))
        assertNull(parseClockOrNull("--01:00"))
        assertNull(parseClockOrNull("12:-5"))
        assertNull(parseClockOrNull("1.5:00"))
    }

    @Test
    fun parse_clock_round_trips_format_clock() {
        val samples = listOf(
            Duration.ZERO,
            5.seconds,
            23.minutes + 45.seconds,
            1.hours + 1.minutes + 1.seconds,
            100.hours,
            -(23.minutes + 45.seconds),
        )
        for (duration in samples) {
            assertEquals(duration, parseClock(duration.formatClock()))
        }
    }

    @Test
    fun durationSumAndAverage() {
        val durations = listOf(1.seconds, 2.seconds, 3.seconds)
        assertEquals(6.seconds, durations.sum())
        assertEquals(2.seconds, durations.average())
        assertEquals(Duration.ZERO, emptyList<Duration>().sum())
        assertFailsWith<NoSuchElementException> { emptyList<Duration>().average() }
    }
}
