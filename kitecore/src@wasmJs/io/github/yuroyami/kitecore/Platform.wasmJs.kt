/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.yuroyami.kitecore

import kotlin.js.ExperimentalWasmJsInterop

internal actual val currentPlatform: Platform = Platform(
    name = "Wasm",
    osVersion = wasmOsVersion(),
    deviceModel = wasmDeviceModel(),
    family = PlatformFamily.WASM,
)

private fun wasmOsVersion(): String = js(
    """(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent
    : (typeof process !== 'undefined' && process.version) ? 'Node ' + process.version
    : ''"""
)

private fun wasmDeviceModel(): String = js(
    """(typeof navigator !== 'undefined' && navigator.userAgentData && navigator.userAgentData.platform) ? navigator.userAgentData.platform
    : (typeof navigator !== 'undefined' && navigator.platform) ? navigator.platform
    : (typeof process !== 'undefined') ? (process.platform + ' ' + process.arch)
    : ''"""
)
