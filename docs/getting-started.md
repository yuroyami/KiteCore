# Getting Started

Set up KiteCore in a Kotlin Multiplatform project and use its core APIs.

## Step 1: Add the dependency

KiteCore is a single artifact. Its only dependency is kotlinx-coroutines-core,
which it exposes transitively (`api`), so you do not need to declare coroutines
yourself.

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yuroyami:kitecore:0.1.0")
        }
    }
}
```

Supported targets: `androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`,
`iosX64`, `macosArm64`, `js(browser, nodejs)`, `wasmJs(browser, nodejs)`.

## Step 2: Move blocking work off the main thread

`ioDispatcher` resolves to the real `Dispatchers.IO` on JVM, Android, iOS, and
macOS. On the web targets it resolves to whatever you install, falling back to
`Dispatchers.Default` with a one-time warning.

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher
import kotlinx.coroutines.withContext

suspend fun loadConfig(path: String): Config =
    withContext(ioDispatcher) {
        parseConfig(readFileBlocking(path))
    }
```

See [IO dispatcher](io-dispatcher.md) for the web setup.

## Step 3: Report progress from a long operation

```kotlin
import io.github.yuroyami.kitecore.asyncWithProgress

val export = scope.asyncWithProgress {
    val fetch = slice(0.4f, "fetching")
    val write = slice(0.6f, "writing")
    val rows = fetchRows { done, total -> fetch.report(done, total) }
    writeRows(rows) { done, total -> write.report(done, total) }
}

launch {
    export.progress.collect { p ->
        // p.fraction: 0.0..1.0 or null, p.phase: "fetching" / "writing"
        updateProgressBar(p)
    }
}
val file = export.await()
```

See [Progress futures](progress-future.md) for cancellation and adapter details.

## Step 4: Use the extension packages

Ten packages under `io.github.yuroyami.kitecore.*` hold over 500 small,
tested utilities. They are ordinary top-level functions and classes: import
what you use.

```kotlin
import io.github.yuroyami.kitecore.text.truncate
import io.github.yuroyami.kitecore.math.formatBytes
import io.github.yuroyami.kitecore.structures.LruCache
import io.github.yuroyami.kitecore.coroutines.retryWithBackoff

val title = longName.truncate(40)
val label = downloaded.formatBytes(decimals = 1)   // "1.5 MiB"
val thumbnails = LruCache<String, ByteArray>(maxSize = 128)
val data = retryWithBackoff(times = 5, initialDelay = 200.milliseconds) {
    api.fetch()
}
```

Each package has its own walkthrough page in the navigation.

## Where next

- [IO dispatcher](io-dispatcher.md): per-target behavior and web setup.
- [Progress futures](progress-future.md): slices, phases, cancellation.
- [Weak references](weakref.md): platform semantics and the support flag.
- [Platform identity](platform.md): branching on the host platform.
- [Web Workers](webworker.md): heavy JavaScript off the main thread.
