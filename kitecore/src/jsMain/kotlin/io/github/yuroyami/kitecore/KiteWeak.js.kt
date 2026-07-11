/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

// Kotlin/JS objects are JS objects, so the ES2021 `WeakRef` holds them directly.
// Feature-detect it; on a pre-ES2021 runtime fall back to a strong hold and
// report isWeakSupported = false (same honest contract as wasmJs). JS primitives
// (String, boxed numbers) are not valid WeakRef targets — the try/catch below
// falls back to a strong hold for those referents.
private val hasWeakRef: Boolean = js("typeof WeakRef !== 'undefined'")

private fun newWeakRefOrNull(o: Any): dynamic =
    js("(function(t){ try { return new WeakRef(t) } catch (e) { return null } })(o)")

private fun derefOrNull(r: dynamic): dynamic =
    js("(function(x){ var v = x.deref(); return v === undefined ? null : v; })(r)")

public actual class KiteWeak<T : Any> actual constructor(referred: T) {

    private var cleared = false
    private val ref: dynamic = if (hasWeakRef) newWeakRefOrNull(referred) else null
    private var strong: T? = if (ref == null) referred else null

    public actual fun get(): T? {
        if (cleared) return null
        strong?.let { return it }
        val v: Any? = derefOrNull(ref)
        @Suppress("UNCHECKED_CAST")
        return v as T?
    }

    public actual fun clear() {
        cleared = true
        strong = null
    }

    public actual companion object {
        public actual val isWeakSupported: Boolean = hasWeakRef
    }
}
