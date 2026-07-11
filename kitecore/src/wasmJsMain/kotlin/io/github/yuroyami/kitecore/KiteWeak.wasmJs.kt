/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

/*
 * Kotlin/Wasm has no weak-reference primitive: the stdlib ships only the strong
 * JsReference<T> handle, and a JS WeakRef cannot hold a wasm-GC object (its
 * wrapper's lifetime is decoupled from the Kotlin object; see FABLE5_AUDIT.md
 * §12). KiteWeak holds its referent strongly here and reports
 * isWeakSupported = false. get() returns the referent until clear(); callers
 * that need real weakness must gate on the flag.
 */
public actual class KiteWeak<T : Any> actual constructor(referred: T) {

    private var strong: T? = referred

    public actual fun get(): T? = strong

    public actual fun clear() {
        strong = null
    }

    public actual companion object {
        public actual val isWeakSupported: Boolean = false
    }
}
