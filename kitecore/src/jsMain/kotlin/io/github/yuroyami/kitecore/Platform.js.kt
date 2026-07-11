/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

actual val platform: Platform = Platform(
    name = "JS",
    osVersion = jsUserAgent(),
    deviceModel = jsPlatformString(),
    family = PlatformFamily.JS,
)

private fun jsUserAgent(): String =
    js("(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent : ''")

private fun jsPlatformString(): String =
    js("(typeof navigator !== 'undefined' && navigator.platform) ? navigator.platform : 'js'")
