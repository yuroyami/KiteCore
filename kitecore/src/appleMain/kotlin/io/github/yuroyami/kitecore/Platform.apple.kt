/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)

package io.github.yuroyami.kitecore

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.Foundation.NSProcessInfo
import platform.darwin.sysctlbyname
import platform.posix.size_tVar

internal actual val currentPlatform: Platform = run {
    val osFamily = kotlin.native.Platform.osFamily
    val (name, family) = when (osFamily) {
        OsFamily.IOS -> "iOS" to PlatformFamily.IOS
        OsFamily.MACOSX -> "macOS" to PlatformFamily.MACOS
        else -> osFamily.name to PlatformFamily.OTHER
    }
    Platform(
        name = name,
        osVersion = osVersionString(),
        deviceModel = deviceModelString(osFamily),
        family = family,
    )
}

private fun osVersionString(): String =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        if (patchVersion == 0L) "$majorVersion.$minorVersion"
        else "$majorVersion.$minorVersion.$patchVersion"
    }

/**
 * `hw.machine` is the device identifier on iOS ("iPhone15,3"). On macOS
 * `hw.machine` is the architecture, so `hw.model` ("Mac14,2") is used instead.
 */
private fun deviceModelString(osFamily: OsFamily): String {
    val key = if (osFamily == OsFamily.MACOSX) "hw.model" else "hw.machine"
    return sysctlString(key) ?: kotlin.native.Platform.cpuArchitecture.name
}

private fun sysctlString(name: String): String? = memScoped {
    val size = alloc<size_tVar>()
    if (sysctlbyname(name, null, size.ptr, null, 0.convert()) != 0) return@memScoped null
    val buf = allocArray<ByteVar>(size.value.toInt())
    if (sysctlbyname(name, buf, size.ptr, null, 0.convert()) != 0) return@memScoped null
    buf.toKString()
}
