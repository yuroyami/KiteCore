/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LruCacheTest {

    @Test
    fun constructorRejectsNonPositiveMaxSize() {
        assertFailsWith<IllegalArgumentException> { LruCache<String, Int>(0) }
        assertFailsWith<IllegalArgumentException> { LruCache<String, Int>(-1) }
    }

    @Test
    fun maxSizeIsExposed() {
        assertEquals(3, LruCache<String, Int>(3).maxSize)
    }

    @Test
    fun putAndGetRoundTrip() {
        val cache = LruCache<String, Int>(2)
        assertNull(cache.put("a", 1))
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("missing"))
    }

    @Test
    fun putReturnsReplacedValueForSameKey() {
        val cache = LruCache<String, Int>(2)
        assertNull(cache.put("a", 1))
        assertEquals(1, cache.put("a", 2))
        assertEquals(2, cache.get("a"))
        assertEquals(1, cache.size)
    }

    @Test
    fun putEvictsLeastRecentlyUsedEntry() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
        assertEquals(2, cache.size)
    }

    @Test
    fun getRefreshesRecency() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun replacingExistingKeyDoesNotEvict() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("a", 10)
        assertEquals(2, cache.size)
        assertEquals(2, cache.get("b"))
    }

    @Test
    fun containsKeyDoesNotRefreshRecency() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        assertTrue(cache.containsKey("a"))
        cache.put("c", 3)
        assertFalse(cache.containsKey("a"))
        assertTrue(cache.containsKey("b"))
        assertFalse(cache.containsKey("missing"))
    }

    @Test
    fun getOrPutComputesOnMissAndCachesOnHit() {
        val cache = LruCache<String, Int>(2)
        var calls = 0
        assertEquals(7, cache.getOrPut("a") { calls++; 7 })
        assertEquals(7, cache.getOrPut("a") { calls++; 99 })
        assertEquals(1, calls)
    }

    @Test
    fun getOrPutHitRefreshesRecency() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.getOrPut("a") { 99 }
        cache.put("c", 3)
        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
    }

    @Test
    fun getOrPutMissCanEvict() {
        val cache = LruCache<String, Int>(1)
        cache.put("a", 1)
        assertEquals(2, cache.getOrPut("b") { 2 })
        assertFalse(cache.containsKey("a"))
    }

    @Test
    fun removeReturnsValueOrNull() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        assertEquals(1, cache.remove("a"))
        assertNull(cache.remove("a"))
        assertEquals(0, cache.size)
    }

    @Test
    fun clearEmptiesTheCache() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("a"))
    }

    @Test
    fun sizeTracksEntries() {
        val cache = LruCache<String, Int>(3)
        assertEquals(0, cache.size)
        cache.put("a", 1)
        assertEquals(1, cache.size)
        cache.put("b", 2)
        cache.put("c", 3)
        cache.put("d", 4)
        assertEquals(3, cache.size)
    }

    @Test
    fun keysAreInEvictionOrderLeastRecentFirst() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache.get("a")
        assertEquals(listOf("b", "c", "a"), cache.keys().toList())
    }

    @Test
    fun toMapIsASnapshotInEvictionOrder() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        val snapshot = cache.toMap()
        assertEquals(listOf("b", "a"), snapshot.keys.toList())
        assertEquals(mapOf("b" to 2, "a" to 1), snapshot)
        cache.put("c", 3)
        assertEquals(2, snapshot.size)
    }
}
