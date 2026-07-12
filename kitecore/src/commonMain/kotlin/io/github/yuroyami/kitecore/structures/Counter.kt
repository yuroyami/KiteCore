/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A multiset that tracks how many times each element has been added.
 *
 * Counts are always positive; removing an element down to zero deletes its
 * key, and querying an absent element yields zero. Iteration and [toMap]
 * follow first-insertion order. [mostCommon] sorts by count descending and
 * breaks ties by first-insertion order. Backed by [LinkedHashMap].
 *
 * This class is not thread-safe.
 *
 * @param T the element type.
 */
public class Counter<T> : Iterable<Map.Entry<T, Int>> {

    private val counts = LinkedHashMap<T, Int>()
    private var totalCount = 0

    /** The sum of the counts of all elements. */
    public val total: Int
        get() = totalCount

    /**
     * Increases the count of [element] by [count].
     *
     * A [count] of zero has no effect and does not create a key.
     *
     * @throws IllegalArgumentException if [count] is negative.
     */
    public fun add(element: T, count: Int = 1) {
        require(count >= 0) { "count must not be negative, was $count" }
        if (count == 0) return
        counts[element] = (counts[element] ?: 0) + count
        totalCount += count
    }

    /**
     * Decreases the count of [element] by [count], flooring at zero.
     *
     * When the count reaches zero the key is removed. Removing an absent
     * element has no effect.
     *
     * @throws IllegalArgumentException if [count] is negative.
     */
    public fun remove(element: T, count: Int = 1) {
        require(count >= 0) { "count must not be negative, was $count" }
        if (count == 0) return
        val current = counts[element] ?: return
        if (count >= current) {
            counts.remove(element)
            totalCount -= current
        } else {
            counts[element] = current - count
            totalCount -= count
        }
    }

    /** Returns the count of [element], or 0 if it is absent. */
    public fun count(element: T): Int = counts[element] ?: 0

    /**
     * Returns up to [n] elements with their counts, highest count first.
     *
     * Ties keep first-insertion order. Runs in time proportional to the
     * number of distinct elements times its logarithm.
     *
     * @throws IllegalArgumentException if [n] is negative.
     */
    public fun mostCommon(n: Int = Int.MAX_VALUE): List<Pair<T, Int>> {
        require(n >= 0) { "n must not be negative, was $n" }
        return counts.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }

    /** Returns a snapshot of the counts in first-insertion order. */
    public fun toMap(): Map<T, Int> = counts.toMap()

    /** Removes every element and resets [total] to zero. */
    public fun clear() {
        counts.clear()
        totalCount = 0
    }

    /** Increases the count of [element] by one. Shorthand for [add]. */
    public operator fun plusAssign(element: T) {
        add(element)
    }

    /**
     * Returns an iterator over a snapshot of the entries in first-insertion
     * order. Mutating the counter after this call does not affect the
     * iterator.
     */
    override fun iterator(): Iterator<Map.Entry<T, Int>> = counts.toMap().entries.iterator()
}
