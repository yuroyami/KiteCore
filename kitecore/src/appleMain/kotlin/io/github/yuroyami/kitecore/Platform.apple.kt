/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalNativeApi::class)

package io.github.yuroyami.kitecore

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import platform.Foundation.NSProcessInfo

actual val platform: Platform = run {
    val osFamily = kotlin.native.Platform.osFamily
    val family = when (osFamily) {
        OsFamily.IOS -> PlatformFamily.IOS
        OsFamily.MACOSX -> PlatformFamily.MACOS
        else -> PlatformFamily.OTHER
    }
    Platform(
        name = osFamily.name,
        osVersion = NSProcessInfo.processInfo.operatingSystemVersionString,
        deviceModel = kotlin.native.Platform.cpuArchitecture.name,
        family = family,
    )
}
