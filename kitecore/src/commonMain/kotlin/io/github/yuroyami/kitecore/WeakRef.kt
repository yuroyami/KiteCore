/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

/**
 * A weak reference to [T]. Holds its referent without preventing garbage
 * collection.
 *
 * [get] returns the referent, or `null` once it has been collected or after
 * [clear]. Query [isWeakSupported] before relying on collection for correctness.
 *
 * Platform behavior:
 * - JVM, Android, Apple/Native: backed by the platform weak reference.
 * - Kotlin/JS: backed by the ES2021 `WeakRef` global. On a runtime older than
 *   ES2021, and for referents backed by JS primitives (`String`, boxed numbers)
 *   that are not valid `WeakRef` targets, the referent is held strongly.
 * - Kotlin/Wasm: no weak primitive exists, so the referent is held strongly and
 *   [isWeakSupported] is `false`.
 *
 * ```kotlin
 * val weak = WeakRef(expensiveThing)
 * val thing = weak.get() ?: rebuild() // null if it was collected
 * ```
 */
public expect class WeakRef<T : Any>(referred: T) {
    /** The referent, or `null` if it has been collected or [clear]ed. */
    public fun get(): T?

    /** Drops the reference. Subsequent calls to [get] return `null`. Idempotent. */
    public fun clear()

    public companion object {
        /**
         * Whether the platform provides real weak semantics.
         *
         * `true` on JVM, Android, Apple/Native, and JS runtimes with `WeakRef`.
         * `false` on Kotlin/Wasm and on JS runtimes older than ES2021, where no
         * weak primitive exists: there [WeakRef] holds its referent strongly and
         * [get] returns non-`null` until [clear].
         */
        public val isWeakSupported: Boolean
    }
}
