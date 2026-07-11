/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

internal actual val currentPlatform: Platform = Platform(
    name = "JVM",
    osVersion = listOfNotNull(
        System.getProperty("os.name"),
        System.getProperty("os.version"),
    ).joinToString(" "),
    deviceModel = System.getProperty("os.arch") ?: "",
    family = PlatformFamily.JVM,
)
