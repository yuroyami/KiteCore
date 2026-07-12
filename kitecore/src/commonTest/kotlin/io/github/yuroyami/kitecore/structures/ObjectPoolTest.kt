/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ObjectPoolTest {

    private class Buffer {
        var dirty: Boolean = false
    }

    @Test
    fun constructorRejectsNegativeCapacity() {
        assertFailsWith<IllegalArgumentException> {
            ObjectPool(capacity = -1, factory = { Buffer() })
        }
    }

    @Test
    fun capacityIsExposed() {
        assertEquals(3, ObjectPool(capacity = 3, factory = { Buffer() }).capacity)
    }

    @Test
    fun borrowCreatesThroughFactoryWhenEmpty() {
        var created = 0
        val pool = ObjectPool(capacity = 2, factory = { created++; Buffer() })
        pool.borrow()
        pool.borrow()
        assertEquals(2, created)
        assertEquals(0, pool.pooledCount)
    }

    @Test
    fun borrowReturnsRecycledInstance() {
        var created = 0
        val pool = ObjectPool(capacity = 2, factory = { created++; Buffer() })
        val instance = pool.borrow()
        pool.recycle(instance)
        assertSame(instance, pool.borrow())
        assertEquals(1, created)
    }

    @Test
    fun recycleResetsBeforeStoring() {
        val pool = ObjectPool(
            capacity = 2,
            factory = { Buffer() },
            reset = { it.dirty = false },
        )
        val instance = pool.borrow()
        instance.dirty = true
        pool.recycle(instance)
        assertEquals(false, pool.borrow().dirty)
    }

    @Test
    fun recycleDropsWithoutResetWhenFull() {
        var resets = 0
        val pool = ObjectPool(
            capacity = 1,
            factory = { Buffer() },
            reset = { resets++ },
        )
        val kept = pool.borrow()
        val dropped = pool.borrow()
        pool.recycle(kept)
        pool.recycle(dropped)
        assertEquals(1, pool.pooledCount)
        assertEquals(1, resets)
        assertSame(kept, pool.borrow())
    }

    @Test
    fun zeroCapacityPoolNeverStores() {
        val pool = ObjectPool(capacity = 0, factory = { Buffer() })
        val instance = pool.borrow()
        pool.recycle(instance)
        assertEquals(0, pool.pooledCount)
        assertNotSame(instance, pool.borrow())
    }

    @Test
    fun pooledCountTracksIdleInstances() {
        val pool = ObjectPool(capacity = 2, factory = { Buffer() })
        assertEquals(0, pool.pooledCount)
        val a = pool.borrow()
        val b = pool.borrow()
        pool.recycle(a)
        assertEquals(1, pool.pooledCount)
        pool.recycle(b)
        assertEquals(2, pool.pooledCount)
        pool.borrow()
        assertEquals(1, pool.pooledCount)
    }

    @Test
    fun useReturnsBlockResultAndRecycles() {
        val pool = ObjectPool(capacity = 1, factory = { Buffer() })
        val result = pool.use { instance ->
            instance.dirty = true
            "done"
        }
        assertEquals("done", result)
        assertEquals(1, pool.pooledCount)
    }

    @Test
    fun useRecyclesWhenBlockThrows() {
        val pool = ObjectPool(capacity = 1, factory = { Buffer() })
        assertFailsWith<IllegalStateException> {
            pool.use { throw IllegalStateException("boom") }
        }
        assertEquals(1, pool.pooledCount)
    }

    @Test
    fun useReusesTheRecycledInstance() {
        var created = 0
        val pool = ObjectPool(capacity = 1, factory = { created++; Buffer() })
        pool.use { }
        pool.use { }
        assertEquals(1, created)
    }
}
