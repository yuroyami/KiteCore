# Platform identity

`Platform` describes the host at runtime: a display `name`, an `osVersion`, a `deviceModel`, and a coarse `family` for branching. Read it from `Platform.current`; the class has no public constructor.

## Read the current platform

```kotlin
import io.github.yuroyami.kitecore.Platform

val platform = Platform.current
println(platform.name)         // "iOS"
println(platform.osVersion)    // "17.4"
println(platform.deviceModel)  // "iPhone15,3"
println(platform)              // "iOS 17.4 (iPhone15,3)"
```

`current` is cached and safe to read from any thread.

## Branch on the family

`PlatformFamily` is a coarse enum made for `when` in shared code: `ANDROID`, `JVM`, `IOS`, `MACOS`, `JS`, `WASM`, and `OTHER` as the fallback for hosts outside the named families.

```kotlin
import io.github.yuroyami.kitecore.Platform
import io.github.yuroyami.kitecore.PlatformFamily

val layout = when (Platform.current.family) {
    PlatformFamily.ANDROID, PlatformFamily.IOS -> Layout.Mobile
    PlatformFamily.JVM, PlatformFamily.MACOS -> Layout.Desktop
    PlatformFamily.JS, PlatformFamily.WASM -> Layout.Web
    PlatformFamily.OTHER -> Layout.Desktop
}
```

Branch on `family`, not on `name`, and never by parsing `osVersion` or `deviceModel`; those fields are for display and diagnostics.

## Field values per target

| Target | `name` | `osVersion` | `deviceModel` |
|---|---|---|---|
| Android | `"Android"` | release and API level, e.g. `"15 (API 35)"` | manufacturer and model, e.g. `"Google Pixel 8"` |
| JVM | `"JVM"` | OS name and version, e.g. `"Mac OS X 15.3"` | CPU architecture, e.g. `"aarch64"` |
| iOS | `"iOS"` | OS version, e.g. `"17.4"` | hardware identifier, e.g. `"iPhone15,3"` |
| macOS | `"macOS"` | OS version, e.g. `"15.3.1"` | hardware model, e.g. `"Mac14,2"` |
| js | `"JS"` | browser user agent, or `"Node v22.11.0"` under Node | navigator platform, e.g. `"macOS"`, or `"darwin arm64"` under Node |
| wasmJs | `"Wasm"` | browser user agent, or `"Node v22.11.0"` under Node | navigator platform, e.g. `"macOS"`, or `"darwin arm64"` under Node |

## What toString produces

`toString()` is `"$name $osVersion ($deviceModel)"`, one line suitable for logs and bug reports:

| Target | Example output |
|---|---|
| Android | `Android 15 (API 35) (Google Pixel 8)` |
| JVM | `JVM Mac OS X 15.3 (aarch64)` |
| iOS | `iOS 17.4 (iPhone15,3)` |
| macOS | `macOS 15.3.1 (Mac14,2)` |
| js (browser) | `JS Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ... (macOS)` |
| wasmJs (Node) | `Wasm Node v22.11.0 (darwin arm64)` |

## Expect gaps

Values are best-effort. When a host does not report a field, the field is an empty string rather than a throw:

- A stripped-down web runtime with neither `navigator` nor `process` yields empty `osVersion` and `deviceModel`.
- On iOS and macOS the patch version is omitted when zero (`"17.4"`, not `"17.4.0"`), and the hardware query falls back to the CPU architecture name when the system lookup fails.

Treat every field except `family` as free-form text: log it whole, compare it never.
