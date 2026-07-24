# KiteCore

[![Docs](https://img.shields.io/badge/docs-yuroyami.github.io-1f6feb)](https://yuroyami.github.io/KiteCore/)
![status](https://img.shields.io/badge/status-pre--1.0-orange)
![kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![targets](https://img.shields.io/badge/targets-Android%20%7C%20JVM%20%7C%20iOS%20%7C%20macOS%20%7C%20JS%20%7C%20Wasm-2ea44f)
![license](https://img.shields.io/badge/license-Apache--2.0-blue)

**The runtime toolbox for Kotlin Multiplatform. Five gap-filling core APIs and
more than 500 tested `commonMain` utilities, one dependency, every target.**

> ## 📖 [Read the documentation →](https://yuroyami.github.io/KiteCore/)
> Getting started, a walkthrough for every API, and the full API reference.

KiteCore fills the small runtime holes that Kotlin Multiplatform leaves to each
application. There is no single named IO dispatcher shared across targets, no
built-in way to move blocking work off the JavaScript thread, no common weak
reference, no future that reports progress, and no platform identity value. On
top of those five, KiteCore ships over 500 everyday utilities for text,
collections, flows, coroutines, math, encoding, data structures, time, and
randomness. All of it is common code that runs unchanged on Android, JVM, iOS,
macOS, JS, and Wasm. The only dependency is kotlinx-coroutines.

```kotlin
// commonMain. The same code on every target.
val bytes = withContext(ioDispatcher) { readFileBlocking(path) }   // real Dispatchers.IO

val open = scope.asyncWithProgress {                               // a future with live progress
    val parse = slice(0.4f, "parsing")
    val load  = slice(0.6f, "loading")
    parse.report(1f)
    loadPages { done, total -> load.report(done, total) }
}
launch { open.progress.collect { showBar(it) } }

println(Platform.current)                                          // "iOS 17.5 (iPhone15,3)"

val slug = "Hello, World!".toSlug()                                // "hello-world"
val label = String.format("%2\$s: %1\$,08d", 12_345, "items")      // "items: 0012,345"
val size = downloaded.formatBytes()                               // "1.5 MiB"
val cache = LruCache<String, Bitmap>(maxSize = 64)
```

## Why KiteCore

Every KMP project rewrites the same handful of primitives. It reaches for
`Dispatchers.IO` and finds it is not named the same way on every target. It
needs a weak reference in shared code and discovers the standard library has
none in `commonMain`. It wants a progress bar for a long operation and ends up
inventing a one-off `Flow<Loading|Done>` that restarts the work on re-collect.
Then it rewrites `truncate`, `formatBytes`, `LruCache`, and a retry loop for the
fifth time.

KiteCore is that pile of primitives, written once, tested on every target, and
documented one walkthrough per API. None of it hides a platform trick; it is the
code you would otherwise write yourself, in one place, with a stable public
surface.

## Install

One artifact. Add it to `commonMain`.

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yuroyami:kitecore:0.1.0")
        }
    }
}
```

Its only dependency is `kotlinx-coroutines-core`, exposed transitively, so you do
not declare coroutines yourself. Not using Kotlin Multiplatform? The same
artifact works in a plain Android or JVM project.

## Core APIs

| API | What it does | Targets |
| --- | --- | --- |
| [`ioDispatcher`](docs/io-dispatcher.md) | `Dispatchers.IO` under one name on every target | all |
| [`asyncWithProgress { }`](docs/progress-future.md) | `async`, plus a `StateFlow` of live progress | all |
| [`WeakRef<T>`](docs/weakref.md) | weak reference usable from common code | all |
| [`Platform.current`](docs/platform.md) | OS name, version, device model, family | all |
| [`WebWorker`, `runInWorker`](docs/webworker.md) | run JavaScript off the browser main thread | js, wasmJs |

### ioDispatcher

A `CoroutineDispatcher` for blocking and IO-bound work, under one name on every
target.

```kotlin
val data = withContext(ioDispatcher) { readFileBlocking(path) }
```

| Target | Backing dispatcher |
| --- | --- |
| JVM, Android, iOS, macOS | `Dispatchers.IO` |
| JS, Wasm | the dispatcher passed to `installIoDispatcher(...)`, otherwise `Dispatchers.Default` with a one-time warning |

The web event loop cannot block. Call `installIoDispatcher(...)` once at startup
to back `ioDispatcher` with a real off-thread dispatcher there.
[Full walkthrough →](docs/io-dispatcher.md)

### asyncWithProgress

`asyncWithProgress { }` returns a `ProgressFuture<T>`: a `Deferred<T>` that also
exposes `progress: StateFlow<Progress>`. `Progress` carries a fraction in `0..1`
(or `null` while indeterminate) and a phase label. Progress completes to `1f` on
success and freezes at the last value on failure or cancellation. Divide
multi-stage work with `slice`.

```kotlin
val job = scope.asyncWithProgress {
    repeat(100) { i ->
        delay(10)
        report(done = i + 1L, total = 100)
    }
    "finished"
}
launch { job.progress.collect { p -> println(p) } } // or collectAsState() in Compose
```

[Full walkthrough →](docs/progress-future.md)

### WeakRef

A weak reference that works in common code. `get()` returns the referent, or
`null` once it has been collected or cleared.

```kotlin
val ref = WeakRef(bitmap)
val cached = ref.get() // null once the GC collects it
```

| Target | Semantics |
| --- | --- |
| JVM, Android, Apple, JS (ES2021+) | real weak reference |
| Wasm, JS before ES2021 | held strongly; `WeakRef.isWeakSupported` is `false` |

Check `WeakRef.isWeakSupported` before relying on collection for correctness.
[Full walkthrough →](docs/weakref.md)

### Platform

`Platform.current` identifies the host.

```kotlin
println(Platform.current)   // "Android 14 (API 34) (Google Pixel 8)"

when (Platform.current.family) {
    PlatformFamily.IOS, PlatformFamily.MACOS -> applePath()
    else -> defaultPath()
}
```

Fields: `name`, `osVersion`, `deviceModel`, `family` (`ANDROID`, `JVM`, `IOS`,
`MACOS`, `JS`, `WASM`, `OTHER`). Values are best-effort and may be empty when the
host does not report them. [Full walkthrough →](docs/platform.md)

### WebWorker

Runs a self-contained JavaScript function on a Web Worker, off the main thread.
Available on `js` and `wasmJs` only.

```kotlin
val sum = runInWorker(
    jobJs = "(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }",
    payload = "100000000",
)
```

`runInWorker` builds a worker, runs one payload, and closes it. For repeated
calls, reuse a `WebWorker.of(jobJs)` instance and `call(payload)` it. Calls are
safe to cancel; replies carry correlation ids, so a cancelled call's reply is
never delivered to a later call. [Full walkthrough →](docs/webworker.md)

## Extension packages

Over 500 small utilities in ten packages under `io.github.yuroyami.kitecore.*`.
All common code, all tested on every target. Import what you use.

| Package | Highlights | |
| --- | --- | --- |
| `text` | common `String.format`, `truncate`, `toSnakeCase`, `toSlug`, `mask`, `wrap`, `utf8Size` | [docs](docs/text.md) |
| `collections` | `second()`, `swapped`, `chunkedBy`, `frequencies`, `transposed`, `median`, `percentile` | [docs](docs/collections.md) |
| `flow` | `throttleFirst`, `mapAsync`, `retryWithBackoff`, `windowed`, `combine6..9`, `mapState` | [docs](docs/flow.md) |
| `coroutines` | `retry`, `runCatchingCancellable`, `raceOf`, `parallelMap`, `KeyedMutex`, `suspendLazy` | [docs](docs/coroutines.md) |
| `math` | `roundTo`, `formatDecimals`, `formatBytes`, `lerp`, `gcd`, `hasFlag`, `5.MB` | [docs](docs/math.md) |
| `encoding` | `toHex`, `toBase64`, `crc32`, `fnv1a64`, endian-explicit binary reads and writes | [docs](docs/encoding.md) |
| `structures` | `LruCache`, `TtlCache`, `RingBuffer`, `Counter`, `ObjectPool`, `TypedMap`, `memoize` | [docs](docs/structures.md) |
| `time` | `Stopwatch`, `Deadline`, `RateLimiter`, `formatHms`, `withMinimumDuration`, `pollUntil` | [docs](docs/time.md) |
| `random` | `nextString`, `nextGaussian`, `sample`, `weightedRandom` | [docs](docs/random.md) |
| `util` | `applyIf`, `tryOrNull`, `Result.flatMap`, `useAll`, `weakLazy` | [docs](docs/util.md) |

```kotlin
val id     = Random.nextAlphanumeric(12)
val eta    = (bytesLeft.toDouble() / speed).seconds.formatHms()   // "4m 12s"
val groups = fetchResults().chunkedBy { it.category }
val config = suspendLazy { loadConfigFromDisk() }
val digest = payload.crc32()
retryWithBackoff(times = 5, initialDelay = 200.milliseconds) { api.sync() }
```

## Tested on every target

- Every public API is exercised by tests on all eight target runs: JVM, Android
  host, iOS, macOS, JS (browser and Node), and Wasm (browser and Node).
- The public surface is locked by explicit API mode and a committed ABI dump, so
  an accidental change to a signature fails the build.
- The JVM artifact targets bytecode level 11, so it runs on JDK 11 and later.

## Documentation

The [documentation site](https://yuroyami.github.io/KiteCore/) has a walkthrough
for every API, with a copy-pasteable example whose output is checked against the
code.

- [Getting Started](docs/getting-started.md)
- Core: [ioDispatcher](docs/io-dispatcher.md), [Progress futures](docs/progress-future.md), [WeakRef](docs/weakref.md), [Platform](docs/platform.md), [WebWorker](docs/webworker.md)
- Extensions: [text](docs/text.md), [collections](docs/collections.md), [flow](docs/flow.md), [coroutines](docs/coroutines.md), [math](docs/math.md), [encoding](docs/encoding.md), [structures](docs/structures.md), [time](docs/time.md), [random](docs/random.md), [util](docs/util.md)

## Targets

`androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`,
`js(browser, nodejs)`, `wasmJs(browser, nodejs)`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The public API is guarded by an ABI dump;
regenerate it with `./gradlew :kitecore:updateKotlinAbi` when you change the
surface. Changes ship with tests, and the web targets are where the subtle bugs
live.

## License

Apache-2.0. See [NOTICE](NOTICE). Release notes are in [CHANGELOG.md](CHANGELOG.md).
