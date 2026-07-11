/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.yuroyami.kitecore

import kotlin.js.ExperimentalWasmJsInterop

actual val platform: Platform = Platform(
    name = "Wasm",
    osVersion = wasmUserAgent(),
    deviceModel = wasmPlatformString(),
    family = PlatformFamily.WASM,
)

private fun wasmUserAgent(): String =
    js("(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent : ''")

private fun wasmPlatformString(): String =
    js("(typeof navigator !== 'undefined' && navigator.platform) ? navigator.platform : 'wasm'")
