/*
 * Copyright 2026 yuroyami
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package io.github.yuroyami.kitecore

/** Coarse platform family — useful for `when` branching in shared code. */
enum class PlatformFamily { ANDROID, JVM, IOS, MACOS, JS, WASM, OTHER }

/**
 * Identity of the current runtime platform. The expect/actual `val` every KMP
 * project otherwise re-implements: a readable name, the OS version string, a
 * best-effort device/host model, and a [PlatformFamily] for branching.
 */
class Platform internal constructor(
    val name: String,
    val osVersion: String,
    val deviceModel: String,
    val family: PlatformFamily,
) {
    override fun toString(): String = "$name $osVersion ($deviceModel)"
}

/** The current runtime platform, resolved per target. */
expect val platform: Platform
