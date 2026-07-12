/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.collections

/**
 * Returns a map from each value to its key.
 *
 * When several keys map to the same value, the key of the last entry in iteration
 * order wins. Entries are ordered by first occurrence of each value.
 */
public fun <K, V> Map<K, V>.inverted(): Map<V, K> {
    val result = LinkedHashMap<V, K>()
    for ((key, value) in this) {
        result[value] = key
    }
    return result
}

/**
 * Returns a map from each value to the list of all keys that map to it.
 *
 * Keys inside each list follow this map's iteration order, and values are ordered
 * by first occurrence. No key is lost, unlike [inverted].
 */
public fun <K, V> Map<K, V>.invertedAll(): Map<V, List<K>> {
    val result = LinkedHashMap<V, MutableList<K>>()
    for ((key, value) in this) {
        result.getOrPut(value) { ArrayList() }.add(key)
    }
    return result
}

/**
 * Returns a map containing only the entries whose value is not null, with values typed as non-null.
 *
 * Entry order is preserved. An empty receiver yields an empty map.
 */
public fun <K, V : Any> Map<K, V?>.filterValuesNotNull(): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for ((key, value) in this) {
        if (value != null) result[key] = value
    }
    return result
}

/**
 * Returns a map containing only the entries whose key is not null, with keys typed as non-null.
 *
 * Entry order is preserved. An empty receiver yields an empty map.
 */
public fun <K : Any, V> Map<K?, V>.filterKeysNotNull(): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for ((key, value) in this) {
        if (key != null) result[key] = value
    }
    return result
}

/**
 * Returns a map with each value replaced by the result of [transform], dropping entries where it returns null.
 *
 * Keys are unchanged and entry order is preserved.
 */
public inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    val result = LinkedHashMap<K, R>()
    for (entry in this) {
        val value = transform(entry)
        if (value != null) result[entry.key] = value
    }
    return result
}

/**
 * Returns a map with each key replaced by the result of [transform], dropping entries where it returns null.
 *
 * Values are unchanged. When [transform] produces the same key for several entries,
 * the value of the last one in iteration order wins.
 */
public inline fun <K, V, R : Any> Map<K, V>.mapKeysNotNull(transform: (Map.Entry<K, V>) -> R?): Map<R, V> {
    val result = LinkedHashMap<R, V>()
    for (entry in this) {
        val key = transform(entry)
        if (key != null) result[key] = entry.value
    }
    return result
}

/**
 * Returns a map combining this map with [other], calling [resolve] for keys present in both.
 *
 * [resolve] receives the key, this map's value, and the value from [other], and its
 * result is used for that key. Keys only present in one map keep their value.
 * The receiver is not modified.
 */
public inline fun <K, V> Map<K, V>.mergedWith(other: Map<K, V>, resolve: (key: K, current: V, incoming: V) -> V): Map<K, V> {
    val result = LinkedHashMap<K, V>(this)
    for ((key, value) in other) {
        if (result.containsKey(key)) {
            result[key] = resolve(key, result.getValue(key), value)
        } else {
            result[key] = value
        }
    }
    return result
}

/**
 * Merges [other] into this map in place, calling [resolve] for keys present in both.
 *
 * [resolve] receives the key, this map's value, and the value from [other], and its
 * result is stored for that key. Keys only present in [other] are copied over.
 */
public inline fun <K, V> MutableMap<K, V>.mergeWith(other: Map<K, V>, resolve: (key: K, current: V, incoming: V) -> V) {
    for ((key, value) in other) {
        if (containsKey(key)) {
            this[key] = resolve(key, getValue(key), value)
        } else {
            this[key] = value
        }
    }
}

/** Returns this map if it is not empty, or null otherwise. */
public fun <M : Map<*, *>> M.takeIfNotEmpty(): M? = if (isEmpty()) null else this
