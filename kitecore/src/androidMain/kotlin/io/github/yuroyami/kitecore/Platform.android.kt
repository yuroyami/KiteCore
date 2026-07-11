/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import android.os.Build

actual val platform: Platform = Platform(
    name = "Android",
    osVersion = "Android ${Build.VERSION.RELEASE ?: ""} (API ${Build.VERSION.SDK_INT})",
    deviceModel = ((Build.MANUFACTURER ?: "") + " " + (Build.MODEL ?: "")).trim(),
    family = PlatformFamily.ANDROID,
)
