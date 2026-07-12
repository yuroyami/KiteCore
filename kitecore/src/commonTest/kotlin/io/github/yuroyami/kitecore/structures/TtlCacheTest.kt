/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TtlCacheTest {

    @Test
    fun constructorRejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> {
            TtlCache<String, Int>(ttl = (-1).seconds)
        }
        assertFailsWith<IllegalArgumentException> {
            TtlCache<String, Int>(ttl = 1.seconds, maxSize = 0)
        }
    }

    @Test
    fun ttlAndMaxSizeAreExposed() {
        val cache = TtlCache<String, Int>(ttl = 5.seconds, maxSize = 7)
        assertEquals(5.seconds, cache.ttl)
        assertEquals(7, cache.maxSize)
    }

    @Test
    fun defaultsUseUnboundedSizeAndMonotonicTime() {
        val cache = TtlCache<String, Int>(ttl = 1.hours)
        assertEquals(Int.MAX_VALUE, cache.maxSize)
        cache.put("a", 1)
        assertEquals(1, cache.get("a"))
    }

    @Test
    fun getReturnsValueWhileLiveAndNullOnceExpired() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("a", 1)
        time += 9.seconds
        assertEquals(1, cache.get("a"))
        time += 1.seconds
        assertNull(cache.get("a"))
        assertEquals(0, cache.size)
    }

    @Test
    fun entryExpiresAtExactlyTtl() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("a", 1)
        time += 10.seconds - 1.milliseconds
        assertEquals(1, cache.get("a"))
        time += 1.milliseconds
        assertNull(cache.get("a"))
    }

    @Test
    fun zeroTtlExpiresImmediately() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = Duration.ZERO, timeSource = time)
        cache.put("a", 1)
        assertNull(cache.get("a"))
    }

    @Test
    fun putReplacementRestartsTtl() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("a", 1)
        time += 6.seconds
        cache.put("a", 2)
        time += 6.seconds
        assertEquals(2, cache.get("a"))
    }

    @Test
    fun sizeIncludesExpiredEntriesUntilPurged() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("a", 1)
        cache.put("b", 2)
        time += 11.seconds
        assertEquals(2, cache.size)
        assertEquals(2, cache.purgeExpired())
        assertEquals(0, cache.size)
    }

    @Test
    fun purgeExpiredRemovesOnlyExpiredEntries() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("old", 1)
        time += 6.seconds
        cache.put("fresh", 2)
        time += 5.seconds
        assertEquals(1, cache.purgeExpired())
        assertEquals(1, cache.size)
        assertEquals(2, cache.get("fresh"))
        assertEquals(0, cache.purgeExpired())
    }

    @Test
    fun getOrPutReturnsLiveValueWithoutComputing() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        var calls = 0
        assertEquals(1, cache.getOrPut("a") { calls++; 1 })
        assertEquals(1, cache.getOrPut("a") { calls++; 99 })
        assertEquals(1, calls)
    }

    @Test
    fun getOrPutRecomputesOnceExpired() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        var calls = 0
        cache.getOrPut("a") { calls++; 1 }
        time += 11.seconds
        assertEquals(2, cache.getOrPut("a") { calls++; 2 })
        assertEquals(2, calls)
        assertEquals(2, cache.get("a"))
    }

    @Test
    fun removeReturnsLiveValueAndNullForExpired() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("live", 1)
        cache.put("stale", 2)
        assertEquals(1, cache.remove("live"))
        time += 11.seconds
        assertNull(cache.remove("stale"))
        assertEquals(0, cache.size)
        assertNull(cache.remove("missing"))
    }

    @Test
    fun evictionAtMaxSizeDropsLeastRecentlyUsedLiveEntry() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 1.hours, maxSize = 2, timeSource = time)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)
        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
        assertEquals(2, cache.size)
    }

    @Test
    fun evictionPurgesExpiredEntriesBeforeDroppingLiveOnes() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, maxSize = 2, timeSource = time)
        cache.put("stale", 1)
        time += 11.seconds
        cache.put("live", 2)
        cache.put("new", 3)
        assertEquals(2, cache.get("live"))
        assertEquals(3, cache.get("new"))
        assertEquals(2, cache.size)
    }

    @Test
    fun clearRemovesEverything() {
        val time = TestTimeSource()
        val cache = TtlCache<String, Int>(ttl = 10.seconds, timeSource = time)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("a"))
    }
}
