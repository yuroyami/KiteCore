/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingBufferTest {

    @Test
    fun constructorRejectsNonPositiveCapacity() {
        assertFailsWith<IllegalArgumentException> { RingBuffer<Int>(0) }
        assertFailsWith<IllegalArgumentException> { RingBuffer<Int>(-1) }
    }

    @Test
    fun capacityIsExposed() {
        assertEquals(4, RingBuffer<Int>(4).capacity)
    }

    @Test
    fun addKeepsElementsOldestFirst() {
        val buffer = RingBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        assertEquals(listOf(1, 2, 3), buffer.toList())
    }

    @Test
    fun addOverwritesOldestWhenFull() {
        val buffer = RingBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        buffer.add(4)
        buffer.add(5)
        assertEquals(listOf(3, 4, 5), buffer.toList())
        assertEquals(3, buffer.size)
    }

    @Test
    fun addAllAppendsInIterationOrder() {
        val buffer = RingBuffer<Int>(5)
        buffer.add(0)
        buffer.addAll(listOf(1, 2, 3))
        assertEquals(listOf(0, 1, 2, 3), buffer.toList())
    }

    @Test
    fun addAllBeyondCapacityKeepsOnlyTheNewest() {
        val buffer = RingBuffer<Int>(3)
        buffer.addAll(listOf(1, 2, 3, 4, 5))
        assertEquals(listOf(3, 4, 5), buffer.toList())
    }

    @Test
    fun sizeTracksElementCount() {
        val buffer = RingBuffer<Int>(3)
        assertEquals(0, buffer.size)
        buffer.add(1)
        assertEquals(1, buffer.size)
        buffer.addAll(listOf(2, 3, 4))
        assertEquals(3, buffer.size)
    }

    @Test
    fun isFullAndIsEmptyReflectState() {
        val buffer = RingBuffer<Int>(2)
        assertTrue(buffer.isEmpty())
        assertFalse(buffer.isFull())
        buffer.add(1)
        assertFalse(buffer.isEmpty())
        assertFalse(buffer.isFull())
        buffer.add(2)
        assertTrue(buffer.isFull())
        assertFalse(buffer.isEmpty())
    }

    @Test
    fun clearEmptiesTheBuffer() {
        val buffer = RingBuffer<Int>(3)
        buffer.addAll(listOf(1, 2, 3))
        buffer.clear()
        assertTrue(buffer.isEmpty())
        assertEquals(0, buffer.size)
        assertEquals(emptyList(), buffer.toList())
    }

    @Test
    fun firstAndLastReturnOldestAndNewest() {
        val buffer = RingBuffer<Int>(3)
        buffer.addAll(listOf(1, 2, 3, 4))
        assertEquals(2, buffer.first())
        assertEquals(4, buffer.last())
        assertEquals(2, buffer.firstOrNull())
        assertEquals(4, buffer.lastOrNull())
    }

    @Test
    fun firstAndLastThrowWhenEmpty() {
        val buffer = RingBuffer<Int>(3)
        assertFailsWith<NoSuchElementException> { buffer.first() }
        assertFailsWith<NoSuchElementException> { buffer.last() }
    }

    @Test
    fun firstOrNullAndLastOrNullReturnNullWhenEmpty() {
        val buffer = RingBuffer<Int>(3)
        assertNull(buffer.firstOrNull())
        assertNull(buffer.lastOrNull())
    }

    @Test
    fun iteratorYieldsElementsOldestFirst() {
        val buffer = RingBuffer<Int>(3)
        buffer.addAll(listOf(1, 2, 3, 4))
        val collected = mutableListOf<Int>()
        for (element in buffer) collected.add(element)
        assertEquals(listOf(2, 3, 4), collected)
    }

    @Test
    fun iteratorIsASnapshot() {
        val buffer = RingBuffer<Int>(3)
        buffer.addAll(listOf(1, 2, 3))
        val iterator = buffer.iterator()
        buffer.add(4)
        val collected = mutableListOf<Int>()
        while (iterator.hasNext()) collected.add(iterator.next())
        assertEquals(listOf(1, 2, 3), collected)
    }
}
