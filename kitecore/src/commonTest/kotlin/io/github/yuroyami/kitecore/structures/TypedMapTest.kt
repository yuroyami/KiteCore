/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypedMapTest {

    @Test
    fun typedKeyExposesNameAndToString() {
        val key = TypedKey<Int>("retries")
        assertEquals("retries", key.name)
        assertEquals("TypedKey(retries)", key.toString())
    }

    @Test
    fun setAndGetRoundTripWithTypedValues() {
        val map = TypedMap()
        val intKey = TypedKey<Int>("count")
        val stringKey = TypedKey<String>("label")
        map[intKey] = 42
        map[stringKey] = "kite"
        val count: Int? = map[intKey]
        val label: String? = map[stringKey]
        assertEquals(42, count)
        assertEquals("kite", label)
    }

    @Test
    fun getReturnsNullForAbsentKey() {
        val map = TypedMap()
        assertNull(map[TypedKey<Int>("missing")])
    }

    @Test
    fun setReplacesPreviousValue() {
        val map = TypedMap()
        val key = TypedKey<Int>("count")
        map[key] = 1
        map[key] = 2
        assertEquals(2, map[key])
        assertEquals(1, map.size)
    }

    @Test
    fun keysCompareByIdentityNotName() {
        val map = TypedMap()
        val first = TypedKey<Int>("same")
        val second = TypedKey<Int>("same")
        map[first] = 1
        map[second] = 2
        assertEquals(1, map[first])
        assertEquals(2, map[second])
        assertEquals(2, map.size)
    }

    @Test
    fun removeReturnsValueOrNull() {
        val map = TypedMap()
        val key = TypedKey<String>("label")
        map[key] = "kite"
        assertEquals("kite", map.remove(key))
        assertNull(map.remove(key))
        assertEquals(0, map.size)
    }

    @Test
    fun containsReflectsMembership() {
        val map = TypedMap()
        val key = TypedKey<Int>("count")
        assertFalse(map.contains(key))
        map[key] = 1
        assertTrue(map.contains(key))
        map.remove(key)
        assertFalse(map.contains(key))
    }

    @Test
    fun sizeTracksEntries() {
        val map = TypedMap()
        assertEquals(0, map.size)
        map[TypedKey<Int>("a")] = 1
        map[TypedKey<String>("b")] = "x"
        assertEquals(2, map.size)
    }

    @Test
    fun clearRemovesEverything() {
        val map = TypedMap()
        val key = TypedKey<Int>("count")
        map[key] = 1
        map[TypedKey<String>("label")] = "kite"
        map.clear()
        assertEquals(0, map.size)
        assertNull(map[key])
        assertFalse(map.contains(key))
    }
}
