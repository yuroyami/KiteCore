# KiteCore

Small runtime utilities for Kotlin Multiplatform: the handful of primitives that
are missing from `commonMain`, plus a few coroutine and Flow helpers that are
easy to get wrong.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yuroyami/kitecore)](https://central.sonatype.com/artifact/io.github.yuroyami/kitecore)
[![CI](https://img.shields.io/github/actions/workflow/status/yuroyami/KiteCore/ci.yml?branch=main&label=CI)](https://github.com/yuroyami/KiteCore/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

**[Documentation](https://yuroyami.github.io/KiteCore/)** · a page per API with
runnable examples, plus the generated reference.

## What you get

Kotlin Multiplatform leaves a few small gaps in `commonMain`. There is no name
for `Dispatchers.IO` that resolves on every target, no weak reference, no way to
identify the host platform, no future that reports progress while it runs, and
no `String.format`. Each project fills these in itself, slightly differently.

KiteCore fills them once: four platform primitives, a portable `String.format`,
and a short list of coroutine and Flow helpers. It is one artifact, and its only
dependency is `kotlinx-coroutines-core`.

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher
import io.github.yuroyami.kitecore.Platform
import io.github.yuroyami.kitecore.asyncWithProgress
import io.github.yuroyami.kitecore.text.format

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
println("%.1f%%".format(82.75))        // "82.8%"
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

## The four primitives

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
off-thread dispatcher. KiteCore does not ship one.

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

## Portable `String.format`

The standard library has `String.format` only on the JVM. KiteCore's version
lives in common code, follows Java's `Formatter` syntax, and is tested against
the JVM formatter itself.

```kotlin
import io.github.yuroyami.kitecore.text.format

String.format("%2\$s: %1\$,08d", 12_345, "items")  // "items: 0012,345"
"%-10s %04d".format("retry", 7)                    // "retry      0007"
```

Output does not depend on the device locale. Date and time conversions (`%t`)
are not supported.

## Coroutine and Flow helpers

| Package | API | What it does |
| --- | --- | --- |
| `coroutines` | `retryWithBackoff` | Retries with growing pauses. Never retries cancellation. |
| `coroutines` | `runCatchingCancellable` | `runCatching` that lets cancellation through. |
| `coroutines` | `parallelMap`, `parallelForEach` | Parallel work with a concurrency limit. |
| `coroutines` | `SingleFlight` | Concurrent calls with the same key share one execution. |
| `coroutines` | `suspendLazy` | A lazy value whose initializer can suspend. Failures are not cached. |
| `flow` | `throttleFirst` | Emits the first value, then drops values for a time window. |
| `flow` | `mapAsync` | Maps with a concurrency limit and keeps upstream order. |
| `flow` | `mapState`, `combineStates` | Derived `StateFlow` without a coroutine scope. |

```kotlin
val user = retryWithBackoff(times = 5, initialDelay = 200.milliseconds) { api.fetchUser(id) }
val profiles = userIds.parallelMap(concurrency = 8) { api.fetchProfile(it) }
val config = suspendLazy { loadConfigFromDisk() }
val total: StateFlow<Int> = items.mapState { list -> list.sumOf { it.price } }
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
simulator, macOS, JS (browser and Node) and Wasm (browser and Node). 77 tests.
Every public function and class is referenced by a test.

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
