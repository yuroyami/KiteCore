/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A fixed-capacity cache that evicts the least recently used entry first.
 *
 * [get], [getOrPut] on a hit, and [put] mark the entry most recently used;
 * [containsKey], [keys], and [toMap] do not affect recency. When [put] inserts
 * a new key while the cache already holds [maxSize] entries, the least
 * recently used entry is evicted before the insert. All operations run in
 * amortized constant time. Backed by [LinkedHashMap].
 *
 * This class is not thread-safe.
 *
 * @param K the key type.
 * @param V the value type.
 * @constructor Creates an empty cache that holds at most [maxSize] entries.
 * @throws IllegalArgumentException if [maxSize] is not positive.
 */
public class LruCache<K, V>(
    /** The maximum number of entries the cache holds. Always positive. */
    public val maxSize: Int,
) {

    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    private val map = LinkedHashMap<K, V>()

    /** The number of entries currently in the cache, from 0 to [maxSize]. */
    public val size: Int
        get() = map.size

    /**
     * Returns the value for [key] and marks it most recently used, or returns
     * `null` if the key is absent.
     */
    public fun get(key: K): V? {
        if (!map.containsKey(key)) return null
        @Suppress("UNCHECKED_CAST")
        val value = map.remove(key) as V
        map[key] = value
        return value
    }

    /**
     * Stores [value] under [key] and marks the entry most recently used.
     *
     * Returns the value previously stored under [key], or `null` if the key
     * was absent. Inserting a new key while the cache is at [maxSize] evicts
     * the least recently used entry; the evicted value is discarded, not
     * returned. Replacing an existing key never evicts.
     */
    public fun put(key: K, value: V): V? {
        val previous = map.remove(key)
        map[key] = value
        if (map.size > maxSize) {
            map.remove(map.keys.first())
        }
        return previous
    }

    /**
     * Returns the value for [key] if present, otherwise computes it with
     * [defaultValue], stores it, and returns it.
     *
     * A hit marks the entry most recently used. A miss inserts through [put]
     * and may evict the least recently used entry.
     */
    public fun getOrPut(key: K, defaultValue: () -> V): V {
        if (map.containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            return get(key) as V
        }
        val value = defaultValue()
        put(key, value)
        return value
    }

    /**
     * Removes the entry for [key] and returns its value, or returns `null`
     * if the key is absent.
     */
    public fun remove(key: K): V? = map.remove(key)

    /** Removes every entry from the cache. */
    public fun clear() {
        map.clear()
    }

    /**
     * Returns `true` if [key] has an entry in the cache. Does not affect
     * recency.
     */
    public fun containsKey(key: K): Boolean = map.containsKey(key)

    /**
     * Returns a snapshot of the keys in eviction order, least recently used
     * first. Does not affect recency.
     */
    public fun keys(): Set<K> = map.keys.toSet()

    /**
     * Returns a snapshot of the entries in eviction order, least recently
     * used first. Does not affect recency.
     */
    public fun toMap(): Map<K, V> = map.toMap()
}
