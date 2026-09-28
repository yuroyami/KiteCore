/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun platform_is_populated() {
        val p = Platform.current
        assertTrue(p.name.isNotBlank(), "platform.name should be set")
        assertTrue(p.toString().startsWith(p.name))
        if (p.deviceModel.isNotEmpty()) assertTrue(p.toString().endsWith("(${p.deviceModel})"))
    }

    @Test
    fun osVersion_does_not_duplicate_the_name() {
        val p = Platform.current
        assertFalse(p.osVersion.startsWith(p.name), "osVersion must not repeat name: $p")
    }

    @Test
    fun ios_device_model_is_a_device_not_a_cpu_architecture() {
        val p = Platform.current
        if (p.family != PlatformFamily.IOS) return
        // The simulator reports the host CPU through hw.machine; a device identifier looks like "iPhone15,3".
        assertFalse(p.deviceModel.lowercase() in setOf("arm64", "x86_64", "i386", "x64"), "deviceModel is a CPU architecture: $p")
    }

    @Test
    fun toString_has_no_empty_parentheses() {
        assertFalse(Platform.current.toString().endsWith("()"), "empty device model rendered as (): ${Platform.current}")
    }

    @Test
    fun version_is_exposed() {
        assertTrue(KiteCore.VERSION.isNotBlank())
    }
}
