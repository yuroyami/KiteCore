/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

actual val platform: Platform = Platform(
    name = "JVM",
    osVersion = System.getProperty("java.version") ?: "",
    deviceModel = ((System.getProperty("os.name") ?: "") + " " + (System.getProperty("os.arch") ?: "")).trim(),
    family = PlatformFamily.JVM,
)
