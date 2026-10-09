/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

/**
 * The kind of platform the code runs on, for `when` branches in common code.
 *
 * Read it from [Platform.family]. Branch on this enum, never on the strings of [Platform].
 */
public enum class PlatformFamily {
    /** An Android app or library, on a device or an emulator. */
    ANDROID,

    /** A desktop or server JVM. Android is [ANDROID], not `JVM`. */
    JVM,

    /** iOS, on a device or the simulator. */
    IOS,

    /** macOS, as a Kotlin/Native target. A JVM on a Mac is [JVM]. */
    MACOS,

    /** Kotlin/JS, in a browser or in Node. */
    JS,

    /** Kotlin/Wasm (`wasmJs`), in a browser or in Node. */
    WASM,

    /**
     * An Apple OS other than iOS and macOS. No target that KiteCore builds today
     * returns it, so keep an `OTHER` or `else` branch for targets added later.
     */
    OTHER,
}

/**
 * Where the code runs: the OS name, the OS version, the device model and a `family` to branch on.
 *
 * Get it from [Platform.current]. The class has no public constructor.
 *
 * ### Example
 * ```kotlin
 * println(Platform.current)   // "iOS 17.4 (iPhone15,3)"
 *
 * when (Platform.current.family) {
 *     PlatformFamily.IOS, PlatformFamily.MACOS -> useApplePath()
 *     else -> useDefaultPath()
 * }
 * ```
 *
 * ### Platform differences
 * Each row shows `toString()`, which is [name], then [osVersion], then [deviceModel]
 * in parentheses.
 *
 * | Target | Example |
 * | --- | --- |
 * | Android | `Android 15 (API 35) (Google Pixel 8)` |
 * | JVM | `JVM Mac OS X 15.3 (aarch64)`: the model is the CPU architecture |
 * | iOS | `iOS 17.4 (iPhone15,3)`: the simulator reports the simulated device |
 * | macOS | `macOS 15.3.1 (Mac14,2)` |
 * | JS, Wasm in Node | `Wasm Node v22.11.0 (darwin arm64)` |
 * | JS, Wasm in a browser | `JS <user agent> (macOS)` |
 *
 * ### Watch out
 * - The strings come from the host and can be empty. Show them in logs and bug
 *   reports, but do not parse them. Use [family] in code.
 */
public class Platform internal constructor(
    /** The OS name: `"Android"`, `"JVM"`, `"iOS"`, `"macOS"`, `"JS"` or `"Wasm"`. */
    public val name: String,
    /** The OS version as the host reports it, or empty. For display only. */
    public val osVersion: String,
    /** The device or hardware model as the host reports it, or empty. For display only. */
    public val deviceModel: String,
    /** The platform kind. Use it for `when` branches. */
    public val family: PlatformFamily,
) {
    /** `"name osVersion (deviceModel)"`, without the parts that are empty. */
    override fun toString(): String = buildString {
        append(name)
        if (osVersion.isNotEmpty()) append(' ').append(osVersion)
        if (deviceModel.isNotEmpty()) append(" (").append(deviceModel).append(')')
    }

    public companion object {
        /** The platform that this code runs on. It is read once, and safe to read from any thread. */
        public val current: Platform get() = currentPlatform
    }
}

/** Per-target value backing [Platform.current]. */
internal expect val currentPlatform: Platform
