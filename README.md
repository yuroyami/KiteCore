# KiteCore

Small runtime utilities for Kotlin Multiplatform: the handful of primitives that
are missing from `commonMain`, plus the extension functions most projects end up
writing themselves.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yuroyami/kitecore)](https://central.sonatype.com/artifact/io.github.yuroyami/kitecore)
[![CI](https://img.shields.io/github/actions/workflow/status/yuroyami/KiteCore/ci.yml?branch=main&label=CI)](https://github.com/yuroyami/KiteCore/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

**[Documentation](https://yuroyami.github.io/KiteCore/)** · a page per API with
runnable examples, plus the generated reference.

## What you get

Kotlin Multiplatform leaves a few small gaps in `commonMain`. There is no name
for `Dispatchers.IO` that resolves on every target, no weak reference, no way to
identify the host platform, and no future that reports progress while it runs.
Each project fills these in itself, slightly differently.

KiteCore fills them once: five platform primitives, plus about 450 utility
functions for text, collections, flows, coroutines, math, encoding, time and
randomness. It is one artifact, and its only dependency is
`kotlinx-coroutines-core`.

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher
import io.github.yuroyami.kitecore.Platform
import io.github.yuroyami.kitecore.asyncWithProgress
import io.github.yuroyami.kitecore.text.toSlug
import io.github.yuroyami.kitecore.math.formatBytes

// Blocking work, off the main thread, on every target.
val bytes = withContext(ioDispatcher) { readFileBlocking(path) }

// A Deferred that also publishes progress while it runs.
val job = scope.asyncWithProgress {
    repeat(100) { i ->
        delay(10)
        report(done = i + 1L, total = 100)
    }
    "finished"
}
launch { job.progress.collect { showBar(it.fraction) } }
job.await()

println(Platform.current)              // "iOS 17.5 (iPhone15,3)"
println("Hello, World!".toSlug())      // "hello-world"
println(1_572_864L.formatBytes())      // "1.5 MiB"
```

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

Coroutines come with it transitively, so you do not need to declare them
yourself. The same artifact works in a plain Android or JVM project. You do not have to
be using Kotlin Multiplatform.

## The five primitives

### `ioDispatcher`: one name for blocking work

```kotlin
val data = withContext(ioDispatcher) { readFileBlocking(path) }
```

| Target | Resolves to |
| --- | --- |
| JVM, Android, iOS, macOS | `Dispatchers.IO` |
| JS, Wasm | whatever you passed to `installIoDispatcher(...)` |

**Read this before you use it on web.** Browsers and Node have no OS thread
pool, so there is nothing for `ioDispatcher` to resolve to. Without setup it
falls back to `Dispatchers.Default`. On JS that is the single main thread, so
blocking work there blocks the event loop. You get one console warning, not a
fix. Call `installIoDispatcher(...)` once at startup with a real
off-thread dispatcher, or use `runInWorker` below.

### `asyncWithProgress { }`: a future that reports progress

Returns a `ProgressFuture<T>`: a `Deferred<T>` that also exposes
`progress: StateFlow<Progress>`. A `Progress` carries a fraction in `0..1` (or
`null` while the total is unknown) and a phase label. It reaches `1f` on success
and freezes at its last value on failure or cancellation.

```kotlin
val open = scope.asyncWithProgress {
    val parse = slice(0.4f, "parsing")
    val load = slice(0.6f, "loading")
    parse.report(1f)
    loadPages { done, total -> load.report(done, total) }
}
launch { open.progress.collect { render(it) } }   // or collectAsState() in Compose
```

Use one per user-visible operation, such as opening a document or downloading a
file. Do not use one per inner step.

### `WeakRef<T>`: a weak reference in common code

```kotlin
val ref = WeakRef(bitmap)
val cached = ref.get()   // null once collected
```

| Target | Behavior |
| --- | --- |
| JVM, Android, Apple, JS (ES2021+) | a real weak reference |
| Wasm, older JS | holds strongly; `WeakRef.isWeakSupported` is `false` |

Check `WeakRef.isWeakSupported` before relying on collection for correctness.

### `Platform.current`: what am I running on

```kotlin
println(Platform.current)   // "Android 14 (API 34) (Google Pixel 8)"

when (Platform.current.family) {
    PlatformFamily.IOS, PlatformFamily.MACOS -> applePath()
    else -> defaultPath()
}
```

Fields are `name`, `osVersion`, `deviceModel` and `family`. Values are
best-effort and can be empty when the host does not report them.

### `WebWorker`: run JavaScript off the main thread

Available on `js` and `wasmJs` only.

```kotlin
val sum = runInWorker(
    jobJs = "(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }",
    payload = "100000000",
)
```

`runInWorker` builds a worker, runs one payload and closes it. For repeated
calls keep a `WebWorker.of(jobJs)` and `call(payload)` it. Replies carry
correlation ids, so a cancelled call's reply is never delivered to a later one.

## The utility packages

About 450 functions under `io.github.yuroyami.kitecore.*`. All common code.
Import what you use.

| Package | Some of what's in it |
| --- | --- |
| `text` | `format`, `truncate`, `toSnakeCase`, `toSlug`, `mask`, `wrap`, `utf8Size` |
| `collections` | `second`, `swapped`, `chunkedBy`, `frequencies`, `transposed`, `median`, `percentile` |
| `flow` | `throttleFirst`, `mapAsync`, `retryWithBackoff`, `windowed`, `combine6`–`combine9`, `mapState` |
| `coroutines` | `retry`, `runCatchingCancellable`, `raceOf`, `parallelMap`, `KeyedMutex`, `suspendLazy` |
| `math` | `roundTo`, `formatDecimals`, `formatBytes`, `lerp`, `gcd`, `hasFlag`, `5.MB` |
| `encoding` | `toHex`, `toBase64`, `crc32`, `fnv1a64`, endian-explicit binary reads and writes |
| `structures` | `LruCache`, `TtlCache`, `RingBuffer`, `Counter`, `ObjectPool`, `TypedMap`, `memoize` |
| `time` | `Stopwatch`, `Deadline`, `RateLimiter`, `formatHms`, `withMinimumDuration`, `pollUntil` |
| `random` | `nextString`, `nextGaussian`, `sample`, `weightedRandom` |
| `util` | `applyIf`, `tryOrNull`, `Result.flatMap`, `useAll`, `weakLazy` |

```kotlin
val id = Random.nextAlphanumeric(12)
val eta = (bytesLeft.toDouble() / speed).seconds.formatHms()   // "4m 12s"
val groups = fetchResults().chunkedBy { it.category }
val config = suspendLazy { loadConfigFromDisk() }
retryWithBackoff(times = 5, initialDelay = 200.milliseconds) { api.sync() }
```

## Targets

`androidTarget` (minSdk 21), `jvm` (bytecode level 11, so JDK 11 and later),
`iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`, `js` (browser and
Node), `wasmJs` (browser and Node).

`macosX64` is not built. Kotlin 2.4 deprecated the target.

## Limits

- `ioDispatcher` is only an improvement on web if you call
  `installIoDispatcher(...)`. See the warning above.
- `WeakRef` does not actually hold weakly on Wasm or on JS runtimes older than
  ES2021.
- `Platform` values are best-effort strings, not a stable identifier to branch
  version checks on. Use `family` for that.
- The public surface is locked by explicit API mode and a committed ABI dump, so
  a signature change fails the build. That guard starts at 0.1.0; it does not
  make 0.1.0 a stable API.

## Testing

The public API is exercised on eight target runs: JVM, Android host, iOS
simulator, macOS, JS (browser and Node) and Wasm (browser and Node). 694 tests.
Every public top-level function is referenced by a test except `throttleLatest`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The public API is guarded by an ABI
dump. Regenerate it with `./gradlew :kitecore:updateKotlinAbi` when you change
the surface. Every change ships with tests. Most subtle bugs show up on the web
targets, so test there.

## License

Apache-2.0. See [NOTICE](NOTICE) and [CHANGELOG.md](CHANGELOG.md).

Part of the Kite family: [KitePDF](https://github.com/yuroyami/KitePDF),
[KiteImage](https://github.com/yuroyami/KiteImage),
[KiteQR](https://github.com/yuroyami/KiteQR).
