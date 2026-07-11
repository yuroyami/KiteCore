/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

/** Coarse platform family used for `when` branching in shared code. */
public enum class PlatformFamily { ANDROID, JVM, IOS, MACOS, JS, WASM, OTHER }

/**
 * Identity of the host platform: its [name], [osVersion], [deviceModel], and
 * [family]. Read it from [Platform.current].
 *
 * Field values by target:
 * - Android: [name] is "Android", [osVersion] is the release and API level, [deviceModel] is the manufacturer and model.
 * - JVM: [name] is "JVM", [osVersion] is the OS name and version, [deviceModel] is the CPU architecture.
 * - iOS and macOS: [name] is "iOS" or "macOS", [osVersion] is the OS version, [deviceModel] is the hardware model identifier.
 * - JS and Wasm: [name] is "JS" or "Wasm", [osVersion] is the browser user agent or Node version, [deviceModel] is the navigator or process platform.
 *
 * Values are best-effort and may be empty when a host does not report them.
 *
 * ```kotlin
 * val platform = Platform.current
 * println(platform) // e.g. "iOS 17.4 (iPhone15,3)"
 * ```
 */
public class Platform internal constructor(
    public val name: String,
    public val osVersion: String,
    public val deviceModel: String,
    public val family: PlatformFamily,
) {
    override fun toString(): String = "$name $osVersion ($deviceModel)"

    public companion object {
        /** The host platform for the current target. Cached; safe to read from any thread. */
        public val current: Platform get() = currentPlatform
    }
}

/** Per-target value backing [Platform.current]. */
internal expect val currentPlatform: Platform
