# KiteCore

![status](https://img.shields.io/badge/status-pre--1.0-orange)
![kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![targets](https://img.shields.io/badge/targets-Android%20%7C%20JVM%20%7C%20iOS%20%7C%20macOS%20%7C%20JS%20%7C%20Wasm-2ea44f)
![license](https://img.shields.io/badge/license-Apache--2.0-blue)

KiteCore is a small Kotlin Multiplatform library of runtime primitives that the
standard library and coroutines leave each application to build for itself.

Kotlin Multiplatform has no single named IO dispatcher shared across targets, no
built-in way to move blocking work off the single JavaScript thread, no common
weak reference, no future that reports progress, and no built-in platform
identity value. KiteCore provides one small API for each. Its only dependency is
kotlinx-coroutines.

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

## Quick start

```kotlin
import io.github.yuroyami.kitecore.*
import kotlinx.coroutines.withContext

suspend fun load(path: String): ByteArray {
    println(Platform.current)                 // "iOS 17.5 (iPhone15,3)"
    return withContext(ioDispatcher()) {      // real Dispatchers.IO on JVM/Android/Apple
        readFileBlocking(path)
    }
}
```

## Features

### ioDispatcher

`ioDispatcher()` returns a `CoroutineDispatcher` for blocking or IO-bound work
under one name on every target. `Dispatchers.KiteIO` is shorthand for it.

```kotlin
val bytes = withContext(ioDispatcher()) { readFileBlocking(path) }
withContext(Dispatchers.KiteIO) { /* blocking work */ }
```

| Target | Backing dispatcher |
| --- | --- |
| JVM, Android, Apple | the real `Dispatchers.IO` |
| JS, Wasm | the dispatcher passed to `installIoDispatcher(...)`, otherwise `Dispatchers.Default` with a one-time warning |

The web event loop cannot block, so call `installIoDispatcher(...)` once at
startup to back `ioDispatcher()` with a real off-thread dispatcher.

### KiteWorker

`KiteWorker` runs a self-contained JavaScript function on a Web Worker off the
main thread; `kiteOffload` is the one-shot form that builds a worker, runs one
payload, and closes it. Available on `js` and `wasmJs` with an identical API, and
absent on JVM, Android, and Apple.

```kotlin
val sum = kiteOffload(
    jobJs = "(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }",
    payload = "100000000",
)
```

The job runs in the Worker scope with no access to your Kotlin module; pass data
through the `payload` string. A reusable `KiteWorker.of(...)` instance serializes
concurrent calls, and each reply carries a correlation id, so a reply to a
cancelled call is dropped rather than delivered to the next caller.

### Platform

`Platform.current` identifies the host platform through `name`, `osVersion`,
`deviceModel`, and `family`.

```kotlin
val p = Platform.current
println(p)                          // "Android 14 (API 34) (Google Pixel 8)"
when (p.family) {
    PlatformFamily.IOS -> useIosPath()
    else -> useDefaultPath()
}
```

`family` is one of `ANDROID`, `JVM`, `IOS`, `MACOS`, `JS`, `WASM`, `OTHER`.

### KiteWeak

`KiteWeak<T>` is a weak reference usable from common code; `get()` returns the
referent, or `null` once it has been collected or cleared.

```kotlin
val cache = KiteWeak(expensiveThing)
val thing = cache.get() ?: rebuild()   // null once collected
```

| Target | Semantics |
| --- | --- |
| JVM, Android, Apple, JS with ES2021 `WeakRef` | real weak reference |
| Wasm, and JS runtimes older than ES2021 | referent held strongly; `KiteWeak.isWeakSupported` is `false` |

Check `KiteWeak.isWeakSupported` before relying on collection for correctness.

### KiteFuture

`kiteAsync { }` returns a `KiteFuture<T>`, a `Deferred` that also exposes a
conflated `StateFlow<KiteProgress>` of live progress. Progress completes to a
fraction of `1f` on success and freezes at the last value on failure or
cancellation.

```kotlin
val opening: KiteFuture<PdfDocument> = scope.kiteAsync {
    val xref  = slice(0.3f, "xref")
    val pages = slice(0.7f, "pages")
    val table = parseXref(source) { f -> xref.report(f) }
    loadPages(table) { done, total -> pages.report(done, total) }
}
launch { opening.progress.collect { ui.show(it) } }   // or collectAsState() in Compose
val doc = opening.await()
```

`KiteFuture` is common code and works on every target.

## Targets

`androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`,
`js(browser, nodejs)`, `wasmJs(browser, nodejs)`.

## Roadmap

- A Worker-backed `CoroutineDispatcher` on web, installable via `installIoDispatcher`.
- Filesystem utilities: `writeAtomically`, `readResourceBytesBlocking`, app-directory resolution.

## License

Apache-2.0. See [NOTICE](NOTICE).
