/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CounterTest {

    @Test
    fun addIncrementsByOneByDefault() {
        val counter = Counter<String>()
        counter.add("a")
        counter.add("a")
        assertEquals(2, counter.count("a"))
    }

    @Test
    fun addAcceptsExplicitCount() {
        val counter = Counter<String>()
        counter.add("a", 5)
        assertEquals(5, counter.count("a"))
        assertEquals(5, counter.total)
    }

    @Test
    fun addZeroHasNoEffect() {
        val counter = Counter<String>()
        counter.add("a", 0)
        assertEquals(0, counter.count("a"))
        assertEquals(emptyMap(), counter.toMap())
    }

    @Test
    fun addRejectsNegativeCount() {
        val counter = Counter<String>()
        assertFailsWith<IllegalArgumentException> { counter.add("a", -1) }
    }

    @Test
    fun removeDecrementsAndFloorsAtZero() {
        val counter = Counter<String>()
        counter.add("a", 3)
        counter.remove("a")
        assertEquals(2, counter.count("a"))
        counter.remove("a", 10)
        assertEquals(0, counter.count("a"))
        assertEquals(emptyMap(), counter.toMap())
        assertEquals(0, counter.total)
    }

    @Test
    fun removeAtExactlyZeroDeletesTheKey() {
        val counter = Counter<String>()
        counter.add("a", 2)
        counter.remove("a", 2)
        assertEquals(emptyMap(), counter.toMap())
    }

    @Test
    fun removeAbsentElementHasNoEffect() {
        val counter = Counter<String>()
        counter.remove("missing", 3)
        assertEquals(0, counter.total)
    }

    @Test
    fun removeZeroHasNoEffect() {
        val counter = Counter<String>()
        counter.add("a", 2)
        counter.remove("a", 0)
        assertEquals(2, counter.count("a"))
    }

    @Test
    fun removeRejectsNegativeCount() {
        val counter = Counter<String>()
        assertFailsWith<IllegalArgumentException> { counter.remove("a", -1) }
    }

    @Test
    fun countReturnsZeroForAbsentElement() {
        assertEquals(0, Counter<String>().count("missing"))
    }

    @Test
    fun totalSumsAllCounts() {
        val counter = Counter<String>()
        counter.add("a", 2)
        counter.add("b", 3)
        counter.remove("a")
        assertEquals(4, counter.total)
    }

    @Test
    fun mostCommonSortsByDescendingCount() {
        val counter = Counter<String>()
        counter.add("a", 1)
        counter.add("b", 3)
        counter.add("c", 2)
        assertEquals(listOf("b" to 3, "c" to 2, "a" to 1), counter.mostCommon())
    }

    @Test
    fun mostCommonLimitsToN() {
        val counter = Counter<String>()
        counter.add("a", 1)
        counter.add("b", 3)
        counter.add("c", 2)
        assertEquals(listOf("b" to 3), counter.mostCommon(1))
        assertEquals(emptyList(), counter.mostCommon(0))
    }

    @Test
    fun mostCommonBreaksTiesByInsertionOrder() {
        val counter = Counter<String>()
        counter.add("first", 2)
        counter.add("second", 2)
        counter.add("third", 2)
        assertEquals(
            listOf("first" to 2, "second" to 2, "third" to 2),
            counter.mostCommon(),
        )
    }

    @Test
    fun mostCommonRejectsNegativeN() {
        assertFailsWith<IllegalArgumentException> { Counter<String>().mostCommon(-1) }
    }

    @Test
    fun toMapIsASnapshotInInsertionOrder() {
        val counter = Counter<String>()
        counter.add("a", 1)
        counter.add("b", 2)
        val snapshot = counter.toMap()
        assertEquals(mapOf("a" to 1, "b" to 2), snapshot)
        assertEquals(listOf("a", "b"), snapshot.keys.toList())
        counter.add("c")
        assertEquals(2, snapshot.size)
    }

    @Test
    fun clearRemovesEverything() {
        val counter = Counter<String>()
        counter.add("a", 2)
        counter.clear()
        assertEquals(0, counter.total)
        assertEquals(0, counter.count("a"))
        assertEquals(emptyMap(), counter.toMap())
    }

    @Test
    fun plusAssignIncrementsByOne() {
        val counter = Counter<String>()
        counter += "a"
        counter += "a"
        assertEquals(2, counter.count("a"))
        assertEquals(2, counter.total)
    }

    @Test
    fun iteratorYieldsEntriesInInsertionOrder() {
        val counter = Counter<String>()
        counter.add("a", 1)
        counter.add("b", 2)
        val collected = mutableListOf<Pair<String, Int>>()
        for (entry in counter) collected.add(entry.key to entry.value)
        assertEquals(listOf("a" to 1, "b" to 2), collected)
    }

    @Test
    fun iteratorIsASnapshot() {
        val counter = Counter<String>()
        counter.add("a", 1)
        val iterator = counter.iterator()
        counter.add("b", 2)
        val collected = mutableListOf<String>()
        while (iterator.hasNext()) collected.add(iterator.next().key)
        assertEquals(listOf("a"), collected)
    }
}
