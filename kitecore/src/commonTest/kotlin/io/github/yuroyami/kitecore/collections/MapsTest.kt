/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MapsTest {

    @Test
    fun inverted_swaps_keys_and_values_with_later_keys_winning() {
        assertEquals(mapOf("a" to 1, "b" to 2), mapOf(1 to "a", 2 to "b").inverted())
        assertEquals(
            mapOf("a" to 3, "b" to 2),
            mapOf(1 to "a", 2 to "b", 3 to "a").inverted(),
            "the key of the last entry must win for a shared value",
        )
        assertEquals(emptyMap(), emptyMap<Int, String>().inverted())
    }

    @Test
    fun invertedAll_keeps_every_key_for_a_shared_value() {
        assertEquals(
            mapOf("a" to listOf(1, 3), "b" to listOf(2)),
            mapOf(1 to "a", 2 to "b", 3 to "a").invertedAll(),
        )
        assertEquals(emptyMap(), emptyMap<Int, String>().invertedAll())
    }

    @Test
    fun filterValuesNotNull_drops_null_values_and_tightens_the_type() {
        val source: Map<String, Int?> = mapOf("a" to 1, "b" to null, "c" to 3)
        val filtered: Map<String, Int> = source.filterValuesNotNull()
        assertEquals(mapOf("a" to 1, "c" to 3), filtered)
        assertEquals(emptyMap(), mapOf("a" to null).filterValuesNotNull())
    }

    @Test
    fun filterKeysNotNull_drops_null_keys_and_tightens_the_type() {
        val source: Map<String?, Int> = mapOf("a" to 1, null to 2, "c" to 3)
        val filtered: Map<String, Int> = source.filterKeysNotNull()
        assertEquals(mapOf("a" to 1, "c" to 3), filtered)
        assertEquals(emptyMap(), mapOf(null to 1).filterKeysNotNull())
    }

    @Test
    fun mapValuesNotNull_transforms_and_drops_null_results() {
        val result: Map<String, Int> = mapOf("a" to 1, "b" to 2, "c" to 3)
            .mapValuesNotNull { entry -> if (entry.value % 2 == 0) null else entry.value * 10 }
        assertEquals(mapOf("a" to 10, "c" to 30), result)
    }

    @Test
    fun mapKeysNotNull_transforms_and_drops_null_results() {
        val result: Map<String, String> = mapOf(1 to "a", 2 to "b", 3 to "c")
            .mapKeysNotNull { entry -> if (entry.key == 2) null else "k${entry.key}" }
        assertEquals(mapOf("k1" to "a", "k3" to "c"), result)
    }

    @Test
    fun mergedWith_resolves_conflicts_and_keeps_the_receiver_untouched() {
        val left = mapOf("a" to 1, "b" to 2)
        val merged = left.mergedWith(mapOf("b" to 3, "c" to 4)) { _, current, incoming -> current + incoming }
        assertEquals(mapOf("a" to 1, "b" to 5, "c" to 4), merged)
        assertEquals(mapOf("a" to 1, "b" to 2), left, "receiver must stay untouched")
    }

    @Test
    fun mergedWith_passes_the_key_to_the_resolver() {
        val merged = mapOf("a" to 1).mergedWith(mapOf("a" to 2)) { key, _, _ -> key.length }
        assertEquals(mapOf("a" to 1), merged)
    }

    @Test
    fun mergeWith_resolves_conflicts_in_place() {
        val target = mutableMapOf("a" to 1, "b" to 2)
        target.mergeWith(mapOf("b" to 3, "c" to 4)) { _, current, incoming -> current + incoming }
        assertEquals(mapOf("a" to 1, "b" to 5, "c" to 4), target)
    }

    @Test
    fun takeIfNotEmpty_returns_receiver_or_null() {
        val map = mapOf(1 to "a")
        assertSame(map, map.takeIfNotEmpty())
        assertNull(emptyMap<Int, String>().takeIfNotEmpty())
    }
}
