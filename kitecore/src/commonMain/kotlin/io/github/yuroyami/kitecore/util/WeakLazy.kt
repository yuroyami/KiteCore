/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

import io.github.yuroyami.kitecore.WeakRef

/**
 * A lazily computed value cached through a [WeakRef], so the cached instance can be garbage collected and recomputed later.
 *
 * Reading [value] runs the initializer on first access and whenever the
 * previously cached instance has been collected, so the initializer may run
 * more than once and must be pure enough to tolerate that. Between
 * collections, reads return the same instance. This makes `WeakLazy` a fit
 * for expensive-to-build but reconstructible objects such as parsed
 * resources, caches, or renderers.
 *
 * On targets where [WeakRef.isWeakSupported] is `false` (Kotlin/Wasm, and JS
 * runtimes without ES2021 `WeakRef`) the cached instance is held strongly and
 * is never collected, so after the first read this behaves like a plain
 * [lazy], although [refresh] and [clear] still replace or drop the cache.
 *
 * This class is not thread-safe. Concurrent reads may each run the
 * initializer and observe different instances; confine an instance to one
 * thread or add external synchronization.
 *
 * ```kotlin
 * val table = weakLazy { loadHyphenationTable() }
 * table.value.hyphenate(word) // recomputed transparently if collected
 * ```
 *
 * @param initializer computes the value; invoked on first read and after each
 *   collection, [refresh], or [clear].
 */
public class WeakLazy<T : Any>(private val initializer: () -> T) {

    private var ref: WeakRef<T>? = null

    /**
     * The current value, computing and caching it when absent.
     *
     * Returns the cached instance while it is alive; otherwise runs the
     * initializer, caches the fresh instance weakly, and returns it. Hold the
     * returned value in a local while using it, because reading [value] twice
     * may compute twice once the first instance becomes unreachable.
     */
    public val value: T
        get() = ref?.get() ?: computeAndCache()

    /**
     * Discards any cached instance, recomputes eagerly, and returns the fresh value.
     *
     * Use this when the underlying data changed and the next reader must not
     * observe a stale cached instance.
     *
     * @return the newly computed instance, now cached.
     */
    public fun refresh(): T = computeAndCache()

    /**
     * Returns the currently cached instance, or `null`, without computing.
     *
     * `null` means the value was never computed, was [clear]ed, or has been
     * garbage collected.
     */
    public fun peek(): T? = ref?.get()

    /**
     * Drops the cached instance without computing a replacement.
     *
     * The next read of [value] runs the initializer again. On targets without
     * weak reference support this is the only way the cached instance is
     * released.
     */
    public fun clear() {
        ref?.clear()
        ref = null
    }

    private fun computeAndCache(): T {
        val computed = initializer()
        ref = WeakRef(computed)
        return computed
    }
}

/**
 * Returns a [WeakLazy] that computes its value with [initializer].
 *
 * See [WeakLazy] for the caching, collection, and thread-safety contract.
 *
 * ```kotlin
 * val icons = weakLazy { decodeIconAtlas() }
 * ```
 *
 * @param initializer computes the value; may run more than once.
 */
public fun <T : Any> weakLazy(initializer: () -> T): WeakLazy<T> = WeakLazy(initializer)
