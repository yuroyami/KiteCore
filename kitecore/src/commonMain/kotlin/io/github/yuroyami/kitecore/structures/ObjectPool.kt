/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A bounded pool that reuses instances instead of creating new ones.
 *
 * [borrow] returns a pooled instance when one is available and otherwise
 * creates a fresh one with [factory]. [recycle] resets an instance with the
 * reset callback and stores it, unless the pool already holds [capacity]
 * instances, in which case the instance is dropped without reset. The pool
 * never verifies that a recycled instance came from [borrow].
 *
 * This class is not thread-safe.
 *
 * @param T the pooled instance type.
 * @constructor Creates an empty pool. No instance is created until the first
 * [borrow] on an empty pool.
 * @throws IllegalArgumentException if [capacity] is negative.
 */
public class ObjectPool<T>(
    /** The maximum number of idle instances the pool stores. */
    public val capacity: Int,
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
) {

    init {
        require(capacity >= 0) { "capacity must not be negative, was $capacity" }
    }

    private val pool = ArrayDeque<T>()

    /** The number of idle instances currently stored, from 0 to [capacity]. */
    public val pooledCount: Int
        get() = pool.size

    /**
     * Returns a pooled instance, or a new instance from the factory when the
     * pool is empty.
     *
     * The most recently recycled instance is returned first.
     */
    public fun borrow(): T {
        if (pool.isEmpty()) return factory()
        return pool.removeLast()
    }

    /**
     * Resets [instance] and stores it for a later [borrow].
     *
     * When the pool already holds [capacity] instances, [instance] is dropped
     * and the reset callback is not invoked.
     */
    public fun recycle(instance: T) {
        if (pool.size >= capacity) return
        reset(instance)
        pool.addLast(instance)
    }

    /**
     * Borrows an instance, runs [block] with it, and recycles it, including
     * when [block] throws.
     *
     * Returns the result of [block]. The instance must not be used after
     * [block] returns.
     */
    public inline fun <R> use(block: (T) -> R): R {
        val instance = borrow()
        try {
            return block(instance)
        } finally {
            recycle(instance)
        }
    }
}
