/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalNativeApi::class)

package io.github.yuroyami.kitecore

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

// kotlin.native.ref.WeakReference covers all native targets; it lives in
// nativeMain, not an Apple-only source set. This actual sits in appleMain
// because KiteCore currently builds only Apple native targets.
public actual class KiteWeak<T : Any> actual constructor(referred: T) {

    private val ref = WeakReference(referred)

    public actual fun get(): T? = ref.get()

    public actual fun clear() {
        ref.clear()
    }

    public actual companion object {
        public actual val isWeakSupported: Boolean = true
    }
}
