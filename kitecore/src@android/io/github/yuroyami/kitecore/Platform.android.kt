/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import android.os.Build

internal actual val currentPlatform: Platform = Platform(
    name = "Android",
    osVersion = "${Build.VERSION.RELEASE ?: ""} (API ${Build.VERSION.SDK_INT})".trim(),
    deviceModel = "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: ""}".trim(),
    family = PlatformFamily.ANDROID,
)
