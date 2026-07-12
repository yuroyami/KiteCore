/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

// Identical to the jvmMain copy. KiteCore has no shared jvm/android source set;
// keep the two in sync.
public actual class WeakRef<T : Any> actual constructor(referred: T) {

    private val ref = java.lang.ref.WeakReference(referred)

    public actual fun get(): T? = ref.get()

    public actual fun clear(): Unit = ref.clear()

    public actual companion object {
        public actual val isWeakSupported: Boolean = true
    }
}
