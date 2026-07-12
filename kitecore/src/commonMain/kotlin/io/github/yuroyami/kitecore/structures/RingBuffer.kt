/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A fixed-capacity buffer that overwrites its oldest element when full.
 *
 * Elements are ordered oldest first. [add] appends in constant amortized
 * time and, when the buffer already holds [capacity] elements, drops the
 * oldest one. Iteration and [toList] yield elements oldest first. Backed by
 * [ArrayDeque].
 *
 * This class is not thread-safe.
 *
 * @param T the element type.
 * @constructor Creates an empty buffer that holds at most [capacity] elements.
 * @throws IllegalArgumentException if [capacity] is not positive.
 */
public class RingBuffer<T>(
    /** The maximum number of elements the buffer holds. Always positive. */
    public val capacity: Int,
) : Iterable<T> {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val deque = ArrayDeque<T>(capacity)

    /** The number of elements currently in the buffer, from 0 to [capacity]. */
    public val size: Int
        get() = deque.size

    /**
     * Appends [element] as the newest element, dropping the oldest element
     * first when the buffer is full.
     */
    public fun add(element: T) {
        if (deque.size == capacity) {
            deque.removeFirst()
        }
        deque.addLast(element)
    }

    /**
     * Appends every element of [elements] in iteration order through [add].
     *
     * When [elements] yields more items than [capacity], only the last
     * [capacity] of them remain.
     */
    public fun addAll(elements: Iterable<T>) {
        for (element in elements) add(element)
    }

    /** Returns a snapshot of the elements, oldest first. */
    public fun toList(): List<T> = deque.toList()

    /** Returns `true` if the buffer holds exactly [capacity] elements. */
    public fun isFull(): Boolean = deque.size == capacity

    /** Returns `true` if the buffer holds no elements. */
    public fun isEmpty(): Boolean = deque.isEmpty()

    /** Removes every element from the buffer. */
    public fun clear() {
        deque.clear()
    }

    /**
     * Returns the oldest element.
     *
     * @throws NoSuchElementException if the buffer is empty.
     */
    public fun first(): T = deque.first()

    /** Returns the oldest element, or `null` if the buffer is empty. */
    public fun firstOrNull(): T? = deque.firstOrNull()

    /**
     * Returns the newest element.
     *
     * @throws NoSuchElementException if the buffer is empty.
     */
    public fun last(): T = deque.last()

    /** Returns the newest element, or `null` if the buffer is empty. */
    public fun lastOrNull(): T? = deque.lastOrNull()

    /**
     * Returns an iterator over a snapshot of the elements, oldest first.
     * Mutating the buffer after this call does not affect the iterator.
     */
    override fun iterator(): Iterator<T> = deque.toList().iterator()
}
