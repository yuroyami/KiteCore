# KiteCore

![status](https://img.shields.io/badge/status-pre--1.0-orange)
![kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![targets](https://img.shields.io/badge/targets-Android%20%7C%20JVM%20%7C%20iOS%20%7C%20macOS%20%7C%20JS%20%7C%20Wasm-2ea44f)
![license](https://img.shields.io/badge/license-Apache--2.0-blue)

Runtime utilities for Kotlin Multiplatform: an IO dispatcher on every target,
futures that report progress, weak references in common code, platform identity,
and Web Worker offload on the web targets. The only dependency is
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

## APIs at a glance

| API | What it does | Targets |
| --- | --- | --- |
| `ioDispatcher` | `Dispatchers.IO` under one name on every target | all |
| `asyncWithProgress { }` | `async`, plus a `StateFlow` of live progress | all |
| `WeakRef<T>` | weak reference usable from common code | all |
| `Platform.current` | OS name, version, device model, family | all |
| `WebWorker`, `runInWorker` | run JavaScript off the browser main thread | js, wasmJs |

## ioDispatcher

A `CoroutineDispatcher` for blocking and IO-bound work, under one name on every
target.

```kotlin
val data = withContext(ioDispatcher) {
    // blocking work
}
```

| Target | Backing dispatcher |
| --- | --- |
| JVM, Android, iOS, macOS | `Dispatchers.IO` |
| JS, Wasm | the dispatcher passed to `installIoDispatcher(...)`, otherwise `Dispatchers.Default` with a one-time warning |

The web event loop cannot block. Call `installIoDispatcher(...)` once at startup
to back `ioDispatcher` with a real off-thread dispatcher there.

## asyncWithProgress

`asyncWithProgress { }` starts a coroutine like `async` and returns a
`ProgressFuture<T>`: a `Deferred<T>` that also exposes
`progress: StateFlow<Progress>`. `Progress` carries a fraction in `0..1` (or
`null` while indeterminate) and an optional phase label.

```kotlin
val job = scope.asyncWithProgress {
    repeat(100) { i ->
        delay(10) // the actual work
        report(done = i + 1L, total = 100)
    }
    "finished"
}
launch { job.progress.collect { p -> println(p) } } // or collectAsState() in Compose
println(job.await()) // "finished"
```

Progress completes to `1f` on success and freezes at the last value on failure
or cancellation. Split multi-stage work with `slice`:

```kotlin
scope.asyncWithProgress {
    val decode = slice(0.3f, "decode")
    val render = slice(0.7f, "render")
    decode.report(1f)          // overall progress: 0.3
    render.report(done = 1, total = 2) // overall progress: 0.65
}
```

`report` is safe to call from any thread. `slice` must be called sequentially.
Wrap an existing API with `deferred.withProgress(stateFlow)`.

## WeakRef

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

Check `WeakRef.isWeakSupported` before relying on collection for correctness. On
JS, referents backed by primitives (`String`, numbers) are held strongly because
they are not valid `WeakRef` targets.

## Platform

`Platform.current` identifies the host.

```kotlin
println(Platform.current)   // "Android 14 (API 34) (Google Pixel 8)"
println(Platform.current)   // "iOS 17.5 (iPhone15,3)"

when (Platform.current.family) {
    PlatformFamily.IOS, PlatformFamily.MACOS -> applePath()
    else -> defaultPath()
}
```

Fields: `name`, `osVersion`, `deviceModel`, `family` (`ANDROID`, `JVM`, `IOS`,
`MACOS`, `JS`, `WASM`, `OTHER`). Values are best-effort and may be empty when the
host does not report them.

## WebWorker

Runs a self-contained JavaScript function on a Web Worker, off the main thread.
Available on `js` and `wasmJs` only.

```kotlin
val sum = runInWorker(
    jobJs = "(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }",
    payload = "100000000",
)
```

`runInWorker` builds a worker, runs one payload, and closes it. For repeated
calls, create a reusable instance with `WebWorker.of(jobJs)` and `call(payload)`
it; `close()` releases it. The job has no access to your Kotlin code: pass data
in through the payload string. Calls are safe to cancel; replies carry
correlation ids, so a cancelled call's reply is never delivered to a later call.
Failures throw `WebWorkerException`.

## Extension packages

Over 500 small, tested utilities in ten packages under
`io.github.yuroyami.kitecore.*`. All of them are common code and run on every
target. Import what you use; nothing loads otherwise.

| Package | Highlights |
| --- | --- |
| `text` | `truncate`, `toSnakeCase`, `toSlug`, `mask`, `wrap`, `toIntOrDefault`, `utf8Size` |
| `collections` | `second()`, `swapped`, `chunkedBy`, `frequencies`, `transposed`, `median`, `percentile` |
| `flow` | `throttleFirst`, `mapAsync`, `retryWithBackoff`, `windowed`, `combine6..9`, `mapState`, `combineStates` |
| `coroutines` | `retry`, `runCatchingCancellable`, `raceOf`, `parallelMap`, `KeyedMutex`, `SingleFlight`, `suspendLazy` |
| `math` | `roundTo`, `formatDecimals`, `formatBytes`, `lerp`, `gcd`, `midpoint`, `hasFlag`, `5.MB` |
| `encoding` | `toHex`, `toBase64`, `crc32`, `fnv1a64`, endian-explicit `readIntLe`/`writeLongBe` |
| `structures` | `LruCache`, `TtlCache`, `RingBuffer`, `Counter`, `ObjectPool`, `TypedMap`, `memoize` |
| `time` | `Stopwatch`, `Deadline`, `RateLimiter`, `formatHms`, `withMinimumDuration`, `pollUntil` |
| `random` | `nextString`, `nextGaussian`, `sample`, `weightedRandom` |
| `util` | `applyIf`, `tryOrNull`, `Result.flatMap`, `useAll`, `weakLazy` |

Examples of the flavor:

```kotlin
val id = Random.nextAlphanumeric(12)
val eta = (bytesLeft.toDouble() / speed).seconds.formatHms()   // "4m 12s"
val page = fetchResults().chunkedBy { it.category }
val config = suspendLazy { loadConfigFromDisk() }
val hash = payload.crc32()
retryWithBackoff(times = 5, initialDelay = 200.milliseconds) { api.sync() }
```

## Targets

`androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`,
`js(browser, nodejs)`, `wasmJs(browser, nodejs)`.

## License

Apache-2.0. See [NOTICE](NOTICE).
