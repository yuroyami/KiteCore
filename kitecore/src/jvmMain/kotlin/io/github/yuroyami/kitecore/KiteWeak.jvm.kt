/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

// Identical to the androidMain copy — KiteCore has no shared jvm/android source
// set. Keep the two in sync (as with the js/wasmJs KiteWorker pair).
public actual class KiteWeak<T : Any> actual constructor(referred: T) {

    private val ref = java.lang.ref.WeakReference(referred)

    public actual fun get(): T? = ref.get()

    public actual fun clear(): Unit = ref.clear()

    public actual companion object {
        public actual val isWeakSupported: Boolean = true
    }
}
