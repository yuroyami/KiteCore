/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

/**
 * A reference that does not keep its object alive, from common code.
 *
 * The object that a `WeakRef` points to is its referent. While other code holds the
 * referent, [get] returns it. After the garbage collector frees it, [get] returns
 * `null`. Use it for caches and listeners that must not cause a memory leak.
 *
 * ### Example
 * ```kotlin
 * val ref = WeakRef(bitmap)
 * // get() returns null after the bitmap was collected.
 * val image = ref.get() ?: reloadBitmap()
 * ```
 *
 * ### Platform differences
 * | Target | Weak? |
 * | --- | --- |
 * | JVM, Android, iOS, macOS | Yes, backed by the platform weak reference |
 * | JS, ES2021 and later | Yes, backed by the JS `WeakRef`, except for strings and numbers |
 * | JS before ES2021 | No, it holds the referent strongly |
 * | Wasm | No, it holds the referent strongly |
 *
 * ### Watch out
 * - Always handle the `null` from [get]. The collector can free the referent at any
 *   time after the last strong reference is gone.
 * - Where the referent is held strongly, only [clear] frees it. If your design needs
 *   the memory back, check [isWeakSupported].
 * - On JS, strings and boxed numbers are held strongly even when [isWeakSupported] is
 *   `true`. Wrap them in your own class when that matters.
 *
 * @param referred the referent.
 */
public expect class WeakRef<T : Any>(referred: T) {
    /** Returns the referent, or `null` after it was collected or after [clear]. */
    public fun get(): T?

    /** Drops the referent now. Every later [get] returns `null`. A second call does nothing. */
    public fun clear()

    public companion object {
        /**
         * `true` when this platform has real weak references, so the collector can free
         * a referent.
         *
         * `false` on Wasm and on JS runtimes older than ES2021. There [WeakRef] holds
         * its referent strongly, and [get] returns it until [clear].
         *
         * ```kotlin
         * val cache = if (WeakRef.isWeakSupported) WeakCache() else SizeLimitedCache(8)
         * ```
         */
        public val isWeakSupported: Boolean
    }
}
