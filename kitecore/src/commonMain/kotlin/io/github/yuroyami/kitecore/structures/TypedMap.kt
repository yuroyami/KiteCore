/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A heterogeneous map whose values are typed by their [TypedKey].
 *
 * Each entry is stored under a `TypedKey<T>` and read back as `T`, so a
 * single map can hold values of unrelated types without casts at the call
 * site. Keys compare by identity; see [TypedKey]. Operations run in expected
 * constant time.
 *
 * This class is not thread-safe.
 *
 * @constructor Creates an empty map.
 */
public class TypedMap {

    private val map = HashMap<TypedKey<*>, Any?>()

    /** The number of entries currently in the map. */
    public val size: Int
        get() = map.size

    /**
     * Returns the value stored under [key], or `null` if the key is absent.
     */
    public operator fun <T> get(key: TypedKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as T?
    }

    /** Stores [value] under [key], replacing any previous value. */
    public operator fun <T> set(key: TypedKey<T>, value: T) {
        map[key] = value
    }

    /**
     * Removes the entry for [key] and returns its value, or returns `null`
     * if the key is absent.
     */
    public fun <T> remove(key: TypedKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return map.remove(key) as T?
    }

    /** Returns `true` if [key] has an entry in the map. */
    public fun contains(key: TypedKey<*>): Boolean = map.containsKey(key)

    /** Removes every entry from the map. */
    public fun clear() {
        map.clear()
    }
}
