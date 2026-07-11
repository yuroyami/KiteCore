/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

/**
 * A weak reference to [T]: holds its referent without preventing garbage
 * collection. [get] returns the referent, or `null` once it has been collected
 * (or after [clear]).
 *
 * Kotlin ships no common `WeakReference`. The JVM/Android and Kotlin/Native
 * stdlibs each have one; Kotlin/JS exposes only the ES2021 `WeakRef` global; and
 * Kotlin/Wasm has no weak primitive at all. [KiteWeak] unifies the first three
 * and degrades honestly on the fourth — see [isWeakSupported].
 *
 * On Kotlin/JS, values backed by JS primitives (`String`, boxed numbers, …)
 * cannot be `WeakRef` targets and are held strongly instead — a value with no
 * observable identity lifecycle is simply never "collected".
 *
 * ```
 * val weak = KiteWeak(expensiveThing)
 * // …later…
 * val thing = weak.get() ?: rebuild()   // null if it was collected
 * ```
 */
public expect class KiteWeak<T : Any>(referred: T) {
    /** The referent, or `null` if it has been collected or [clear]ed. */
    public fun get(): T?

    /** Drop the reference eagerly; [get] returns `null` afterward. Idempotent. */
    public fun clear()

    public companion object {
        /**
         * `true` where the platform provides real weak semantics (JVM, Android,
         * Apple/Native, and JS runtimes with `WeakRef`). `false` on Kotlin/Wasm
         * — and on a JS runtime older than ES2021 — where no weak primitive
         * exists: there [KiteWeak] holds its referent **strongly** and [get]
         * never returns `null` until [clear]. Check this before relying on
         * collection for correctness.
         */
        public val isWeakSupported: Boolean
    }
}
