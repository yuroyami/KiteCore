/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * Returns a function that caches every result of [fn] by argument.
 *
 * The cache is unbounded and lives as long as the returned function. Each
 * distinct argument invokes [fn] once; later calls return the cached result,
 * including a cached `null`. Arguments are compared by [Any.equals] and
 * [Any.hashCode]. The returned function is not thread-safe.
 *
 * @param A the argument type.
 * @param R the result type.
 */
public fun <A, R> memoize(fn: (A) -> R): (A) -> R {
    val cache = HashMap<A, R>()
    return { arg ->
        if (cache.containsKey(arg)) {
            @Suppress("UNCHECKED_CAST")
            val cached = cache[arg] as R
            cached
        } else {
            val result = fn(arg)
            cache[arg] = result
            result
        }
    }
}

/**
 * Returns a function that caches every result of [fn] by argument pair.
 *
 * The cache is unbounded and lives as long as the returned function. Each
 * distinct pair of arguments invokes [fn] once; later calls return the cached
 * result, including a cached `null`. Arguments are compared by [Any.equals]
 * and [Any.hashCode]. The returned function is not thread-safe.
 *
 * @param A the first argument type.
 * @param B the second argument type.
 * @param R the result type.
 */
public fun <A, B, R> memoize(fn: (A, B) -> R): (A, B) -> R {
    val cache = HashMap<Pair<A, B>, R>()
    return { a, b ->
        val key = a to b
        if (cache.containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            val cached = cache[key] as R
            cached
        } else {
            val result = fn(a, b)
            cache[key] = result
            result
        }
    }
}
