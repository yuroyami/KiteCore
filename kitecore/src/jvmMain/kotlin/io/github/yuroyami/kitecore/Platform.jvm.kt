/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

internal actual val currentPlatform: Platform = Platform(
    name = "JVM",
    osVersion = listOfNotNull(
        systemProperty("os.name"),
        systemProperty("os.version"),
    ).joinToString(" "),
    deviceModel = systemProperty("os.arch") ?: "",
    family = PlatformFamily.JVM,
)

// A security manager can deny property reads; identity then falls back to empty values.
private fun systemProperty(key: String): String? =
    try {
        System.getProperty(key)
    } catch (e: SecurityException) {
        null
    }
