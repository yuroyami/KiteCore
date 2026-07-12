# KiteCore

Runtime utilities for Kotlin Multiplatform. One dependency (kotlinx-coroutines),
every KMP target: Android, JVM, iOS, macOS, JS, and Wasm.

## What you get

| API | What it does |
| --- | --- |
| [`ioDispatcher`](io-dispatcher.md) | `Dispatchers.IO` under one name on every target |
| [`asyncWithProgress { }`](progress-future.md) | `async`, plus a `StateFlow` of live progress |
| [`WeakRef<T>`](weakref.md) | weak reference usable from common code |
| [`Platform.current`](platform.md) | OS name, version, device model, family |
| [`WebWorker`](webworker.md) | run JavaScript off the browser main thread |
| [Extension packages](text.md) | 500+ tested utilities: text, collections, flow, coroutines, math, encoding, structures, time, random, util |

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

## Thirty seconds of flavor

```kotlin
// Blocking work off the main thread, on every target:
val bytes = withContext(ioDispatcher) { readFileBlocking(path) }

// A future that reports progress:
val job = scope.asyncWithProgress {
    repeat(100) { i ->
        delay(10)
        report(done = i + 1L, total = 100)
    }
    "finished"
}
launch { job.progress.collect { println(it) } }

// Who am I?
println(Platform.current)   // "iOS 17.5 (iPhone15,3)"

// The small stuff you rewrite in every project:
val slug = "Hello, World!".toSlug()          // "hello-world"
val size = 1_572_864L.formatBytes()          // "1.5 MiB"
val cache = LruCache<String, Bitmap>(maxSize = 64)
```

Start with [Getting Started](getting-started.md), or jump straight to the
feature you need from the navigation.

## Status

Pre-1.0. The public surface is guarded by explicit API mode and a committed ABI
dump; every API is tested on the full target matrix.

## License

Apache-2.0. See [NOTICE](https://github.com/yuroyami/KiteCore/blob/main/NOTICE).
