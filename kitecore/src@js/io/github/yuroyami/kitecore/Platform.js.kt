/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

internal actual val currentPlatform: Platform = Platform(
    name = "JS",
    osVersion = jsOsVersion(),
    deviceModel = jsDeviceModel(),
    family = PlatformFamily.JS,
)

private fun jsOsVersion(): String = js(
    """(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent
    : (typeof process !== 'undefined' && process.version) ? 'Node ' + process.version
    : ''"""
)

private fun jsDeviceModel(): String = js(
    """(typeof navigator !== 'undefined' && navigator.userAgentData && navigator.userAgentData.platform) ? navigator.userAgentData.platform
    : (typeof navigator !== 'undefined' && navigator.platform) ? navigator.platform
    : (typeof process !== 'undefined') ? (process.platform + ' ' + process.arch)
    : ''"""
)
