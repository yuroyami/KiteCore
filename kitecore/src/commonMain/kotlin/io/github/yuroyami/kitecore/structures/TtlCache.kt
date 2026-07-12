/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A cache whose entries expire once [ttl] has elapsed since they were stored.
 *
 * An entry is live while its age is strictly less than [ttl]; at exactly [ttl]
 * it is expired. Expired entries are removed lazily: [get], [getOrPut], and
 * [remove] treat them as absent, and [purgeExpired] sweeps them eagerly.
 * [size] therefore includes expired entries that have not been swept yet.
 *
 * When [put] inserts a new key while the cache holds [maxSize] entries, it
 * first purges expired entries and then, if still full, evicts the least
 * recently used live entry. [get] and a [getOrPut] hit refresh recency;
 * replacing a key with [put] refreshes both recency and the entry's age.
 *
 * Time is measured with the [TimeSource.WithComparableMarks] passed to the
 * constructor, [TimeSource.Monotonic] by default.
 *
 * This class is not thread-safe.
 *
 * @param K the key type.
 * @param V the value type.
 * @constructor Creates an empty cache with the given time-to-live, capacity,
 * and time source.
 * @throws IllegalArgumentException if [ttl] is negative or [maxSize] is not
 * positive.
 */
public class TtlCache<K, V>(
    /** The time-to-live of each entry, measured from when it was stored. */
    public val ttl: Duration,
    /** The maximum number of entries the cache holds. Always positive. */
    public val maxSize: Int = Int.MAX_VALUE,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

    init {
        require(!ttl.isNegative()) { "ttl must not be negative, was $ttl" }
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    private class Entry<E>(val value: E, val storedAt: ComparableTimeMark)

    private val map = LinkedHashMap<K, Entry<V>>()

    private fun Entry<V>.isExpired(): Boolean = storedAt.elapsedNow() >= ttl

    /**
     * The number of stored entries, including expired entries that have not
     * been swept by [purgeExpired] or touched by another operation yet.
     */
    public val size: Int
        get() = map.size

    /**
     * Returns the value for [key] and marks it most recently used, or returns
     * `null` if the key is absent or its entry has expired.
     *
     * An expired entry is removed before `null` is returned.
     */
    public fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (entry.isExpired()) {
            map.remove(key)
            return null
        }
        map.remove(key)
        map[key] = entry
        return entry.value
    }

    /**
     * Stores [value] under [key] with a fresh age and marks the entry most
     * recently used.
     *
     * Replacing an existing key restarts its time-to-live. Inserting a new
     * key while the cache is at [maxSize] first purges expired entries and
     * then, if still full, evicts the least recently used entry.
     */
    public fun put(key: K, value: V) {
        map.remove(key)
        if (map.size >= maxSize) {
            purgeExpired()
            if (map.size >= maxSize) {
                map.remove(map.keys.first())
            }
        }
        map[key] = Entry(value, timeSource.markNow())
    }

    /**
     * Returns the live value for [key] if present, otherwise computes it with
     * [defaultValue], stores it through [put], and returns it.
     *
     * A hit marks the entry most recently used. An expired entry counts as a
     * miss and is replaced.
     */
    public fun getOrPut(key: K, defaultValue: () -> V): V {
        val entry = map[key]
        if (entry != null && !entry.isExpired()) {
            map.remove(key)
            map[key] = entry
            return entry.value
        }
        val value = defaultValue()
        put(key, value)
        return value
    }

    /**
     * Removes the entry for [key] and returns its value if it was still live,
     * or returns `null` if the key is absent or its entry has expired.
     *
     * An expired entry is removed but its stale value is not returned.
     */
    public fun remove(key: K): V? {
        val entry = map.remove(key) ?: return null
        return if (entry.isExpired()) null else entry.value
    }

    /** Removes every entry from the cache, live or expired. */
    public fun clear() {
        map.clear()
    }

    /**
     * Removes every expired entry and returns how many were removed. Runs in
     * time linear in [size].
     */
    public fun purgeExpired(): Int {
        var removed = 0
        val iterator = map.values.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().isExpired()) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }
}
