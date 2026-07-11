# KiteCore

![status](https://img.shields.io/badge/status-pre--1.0-orange)
![kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![targets](https://img.shields.io/badge/targets-Android%20%7C%20JVM%20%7C%20iOS%20%7C%20macOS%20%7C%20JS%20%7C%20Wasm-2ea44f)
![license](https://img.shields.io/badge/license-Apache--2.0-blue)

**The runtime gap-closer for Kotlin Multiplatform.** KMP leaves a handful of gaps
that every app re-solves by hand — no `Dispatchers.IO` you can name everywhere,
no way to push blocking work off the single JS thread, no built-in platform
identity. KiteCore ships those as one small, dependency-light library.

KiteCore is the **runtime** half of a pair. Its sibling, the
[`kmp-ssot`](../kmp-ssot) Gradle plugin, closes **build-time** gaps (platform-tree
injection, interop opt-in propagation, Web Worker codegen). A build-time codegen
pass structurally can't hand you a `CoroutineDispatcher` — so that lives here.

## What it does

- **`ioDispatcher()` — one IO dispatcher, every target.**
  - JVM / Android → the real `Dispatchers.IO`.
  - iOS / macOS → a dedicated fixed thread pool (`Dispatchers.IO` is `internal`
    on Kotlin/Native; this gives blocking work its own threads so it never
    starves the CPU pool).
  - JS / Wasm → the dispatcher you `installIoDispatcher(...)`, else
    `Dispatchers.Default` with a one-time warning (the JS event loop can't block).
  - Sugar: `withContext(Dispatchers.KiteIO) { ... }`.

- **`KiteWorker` — inline Web Worker offload (JS).** The "spin up a Worker from a
  Blob URL at runtime" pattern, packaged once. Push a heavy JS job off the main
  thread and `await` it as a suspend call — no separate bundle, no webpack config.

- **`platform` — platform identity.** `name`, `osVersion`, `deviceModel`,
  `family` (`ANDROID`/`JVM`/`IOS`/`MACOS`/`JS`/`WASM`), resolved per target.

## Install

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yuroyami:kitecore:0.1.0")
        }
    }
}
```

## Use

```kotlin
import io.github.yuroyami.kitecore.*
import kotlinx.coroutines.withContext

// Blocking / IO-bound work, off the main thread, on every target:
val bytes = withContext(ioDispatcher()) { readFileBlocking(path) }

// Heavy compute off the JS main thread (JS target):
val sum = kiteOffload(
    jobJs = "(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }",
    payload = "100000000",
)

// Who am I?
println(platform)            // e.g. "IOS 17.5 (arm64)"  /  "Android 14 (API 34) (Google Pixel 8)"
when (platform.family) { PlatformFamily.IOS -> ...; else -> ... }
```

## Targets

`androidTarget` · `jvm` · `iosArm64` · `iosSimulatorArm64` · `iosX64` ·
`macosArm64` · `js(browser, nodejs)` · `wasmJs(browser, nodejs)`. More are pure
expect/actual additions — open an issue.

## Roadmap

Honest about scope. Shipping next, in payoff order:

- **wasmJs Worker offload** — `KiteWorker` parity on the Wasm target (today JS only).
- **A true Worker-backed `CoroutineDispatcher` on web**, installable via
  `installIoDispatcher`, paired with `kmp-ssot`'s separate-chunk codegen.
- **`writeAtomically` / `readResourceBytesBlocking` / app-dir resolution** —
  the small filesystem utilities every Kite library re-rolls.

## Licence

Apache-2.0. Original work — see [NOTICE](NOTICE).
