<p align="center">
  <img src="art/kitecore-logo.svg" width="150" alt="KiteCore logo">
</p>

<h1 align="center">KiteCore</h1>

<p align="center">
  The small runtime pieces that <code>commonMain</code> does not have, for Kotlin
  Multiplatform: an IO dispatcher, weak references, platform identity, progress
  futures and <code>String.format</code>, plus a few coroutine helpers that are easy to get wrong.
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.yuroyami/kitecore"><img src="https://img.shields.io/maven-central/v/io.github.yuroyami/kitecore?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://github.com/yuroyami/KiteCore/actions/workflows/ci.yml"><img src="https://github.com/yuroyami/KiteCore/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://yuroyami.github.io/KiteCore/"><img src="https://img.shields.io/badge/docs-yuroyami.github.io-1f6feb" alt="Docs"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4.10"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License: Apache-2.0"></a>
</p>

<p align="center">
  <b><a href="https://yuroyami.github.io/KiteCore/">Documentation</a></b> · a page for each API with examples, plus the generated API reference.
</p>

## What you get

Every Kotlin Multiplatform project writes the same few `expect`/`actual` helpers again,
each one a little differently. KiteCore writes them once and tests them on every target.
It is one artifact, and its only dependency is `kotlinx-coroutines-core`.

<table>
<tr>
<td width="33%" valign="top">

**`ioDispatcher`**<br>
One name for blocking work. It is `Dispatchers.IO` where that exists, and your own dispatcher on the web.

</td>
<td width="33%" valign="top">

**`asyncWithProgress`**<br>
An `async` that also publishes its progress as a `StateFlow`, with phases and weighted sub-steps.

</td>
<td width="33%" valign="top">

**`WeakRef`**<br>
A weak reference in common code, backed by the real weak reference of each platform.

</td>
</tr>
<tr>
<td width="33%" valign="top">

**`Platform.current`**<br>
The platform, the OS version and the device model, such as `iOS 18.2 (iPhone16,1)`.

</td>
<td width="33%" valign="top">

**`String.format`**<br>
Java's format syntax in common code. The output is the same as the JVM formatter's, on every target.

</td>
<td width="33%" valign="top">

**Coroutine helpers**<br>
Retry with backoff, bounded `parallelMap`, `SingleFlight`, `suspendLazy`, and a few `Flow` operators.

</td>
</tr>
</table>

Here are four of them together:

```kotlin
import io.github.yuroyami.kitecore.Platform
import io.github.yuroyami.kitecore.asyncWithProgress
import io.github.yuroyami.kitecore.ioDispatcher
import io.github.yuroyami.kitecore.text.format

val download = scope.asyncWithProgress {
    withContext(ioDispatcher) {
        repeat(100) { i ->
            readChunkBlocking(i)
            report(done = i + 1L, total = 100)
        }
    }
    "done"
}

launch { download.progress.collect { println("%.0f%%".format((it.fraction ?: 0f) * 100)) } }
download.await()

println(Platform.current)   // "Android 15 (API 35) (Google Pixel 8)"
```

> [!NOTE]
> KiteCore is not at 1.0 yet, so the API can still change between minor versions.
> Every change is in [CHANGELOG.md](CHANGELOG.md).

## Install

KiteCore is on Maven Central at `0.2.0`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yuroyami:kitecore:0.2.0")
        }
    }
}
```

Good to know:

- `kotlinx-coroutines-core` comes with KiteCore, so you do not add it yourself.
- In a plain Android or JVM project, put the same line in your usual `dependencies { }` block.

> [!IMPORTANT]
> 0.2.0 removed most of the helpers that 0.1.0 had. For each removed helper, the
> [migration table](CHANGELOG.md#migration) names the replacement in the standard library
> or in kotlinx-coroutines.

## A quick tour

### Run blocking work with `ioDispatcher`

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher

val bytes = withContext(ioDispatcher) { readFileBlocking(path) }
```

| Target | `ioDispatcher` is |
| --- | --- |
| JVM, Android, iOS, macOS | `Dispatchers.IO` |
| JS, Wasm | the dispatcher that you pass to `installIoDispatcher(...)` |

> [!WARNING]
> Browsers and Node have no thread pool for blocking work. Without setup, `ioDispatcher`
> falls back to `Dispatchers.Default` and logs one warning. On JS, that dispatcher is the
> main thread. Call `installIoDispatcher(...)` once at startup with a dispatcher that runs
> off the main thread, such as one backed by a Web Worker.

### Report progress with `asyncWithProgress`

`asyncWithProgress` returns a `ProgressFuture`. It holds the running operation as
`deferred`, and its progress as `progress: StateFlow<Progress>`. A `Progress` has a
`fraction` from 0 to 1 (`null` while the total is unknown) and a `phase` label.

```kotlin
import io.github.yuroyami.kitecore.asyncWithProgress

val open = scope.asyncWithProgress {
    val parse = slice(0.4f, "parsing")    // the first 40% of the bar
    val load = slice(0.6f, "loading")     // the last 60%
    parseHeader { parse.report(it) }
    loadPages { done, total -> load.report(done, total) }
}

launch { open.progress.collect { render(it.fraction, it.phase) } }   // or collectAsState() in Compose
val document = open.await()
```

- On success, the progress goes to 1. On failure or cancellation, it stays at its last value.
- A report that arrives after the operation ends has no effect.
- `await()` and `cancel()` are on the future. For `awaitAll` and `select`, use `open.deferred`.

### Hold an object weakly with `WeakRef`

```kotlin
import io.github.yuroyami.kitecore.WeakRef

val ref = WeakRef(bitmap)
ref.get()   // null after the garbage collector takes the bitmap
```

The JVM, Android, Apple and JS (ES2021 and later) hold the object weakly. Wasm has no
weak reference, so there `WeakRef` holds the object strongly and `WeakRef.isWeakSupported`
is `false`.

### Find out where the code runs with `Platform.current`

```kotlin
import io.github.yuroyami.kitecore.Platform
import io.github.yuroyami.kitecore.PlatformFamily

Platform.current.name          // "iOS"
Platform.current.osVersion     // "18.2"
Platform.current.deviceModel   // "iPhone16,1", also on the simulator

when (Platform.current.family) {
    PlatformFamily.IOS, PlatformFamily.MACOS -> useApplePath()
    else -> useDefaultPath()
}
```

A value is empty when the host does not report it. Use `family` in code, and use the
strings for logs and bug reports.

### Format text with `String.format`

```kotlin
import io.github.yuroyami.kitecore.text.format

"%-8s|%6.2f|%,d".format("total", 3.14159, 1234567)   // "total   |  3.14|1,234,567"
String.format("%2\$s: %1\$08X", 255, "mask")            // "mask: 000000FF"
```

The syntax is the syntax of `java.util.Formatter`, with flags, width, precision, argument
indexes and the numeric conversions. The output does not depend on the device locale. A bad
pattern throws `StringFormatException`. Date and time conversions (`%t`) are not supported.

### Retry, run in parallel and share work

```kotlin
import io.github.yuroyami.kitecore.coroutines.*

val user = retryWithBackoff(times = 5, initialDelay = 200.milliseconds) {
    withTimeout(2.seconds) { api.fetchUser(id) }   // a timeout here is retried
}

val profiles = userIds.parallelMap(concurrency = 8) { api.fetchProfile(it) }

val tokens = SingleFlight<String, Token>()
val token = tokens.run("session") { api.refreshToken() }   // callers at the same time share one call

val config = suspendLazy { loadConfigFromDisk() }
config.get()
```

| API | What it does |
| --- | --- |
| `retryWithBackoff` | Runs a block again after a failure, with a pause that grows each time. |
| `runCatchingCancellable` | Like `runCatching`, but the cancellation of the caller still stops the caller. |
| `parallelMap`, `parallelForEach` | Run a block for each element, at most `concurrency` at a time. |
| `SingleFlight` | Callers that ask for the same key at the same time share one run of the block. |
| `suspendLazy` | A lazy value with a suspending initializer. A failure is not cached. |

The cancellation of the caller always stops these helpers. A `CancellationException`
from inside the block, such as a `withTimeout` timeout, counts as an ordinary failure. So
`retryWithBackoff` retries it, and `runCatchingCancellable` returns it as a failure.

### Derive and limit flows

```kotlin
import io.github.yuroyami.kitecore.flow.*

val total: StateFlow<Int> = cart.mapState { items -> items.sumOf { it.price } }
val canPay: StateFlow<Boolean> = combineStates(total, balance) { t, b -> b >= t }

clicks.throttleFirst(500.milliseconds)              // one click per half second
urls.mapAsync(concurrency = 4) { download(it) }     // four downloads at a time, in order
```

`mapState` and `combineStates` need no `CoroutineScope`. They compute the value when you
read it.

## Platforms

| Target | Notes |
| --- | --- |
| Android | minSdk 21 |
| JVM | bytecode 11, so JDK 11 and later |
| iOS | `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| macOS | `macosArm64`. Kotlin 2.4 deprecated `macosX64`, so KiteCore does not build it. |
| JS | browser and Node |
| Wasm | `wasmJs`, browser and Node |

## Good to know

<details>
<summary><b>Known limits</b>: what KiteCore does not do, in one table</summary>
<br>

| Topic | What to expect |
| --- | --- |
| `ioDispatcher` on the web | It helps only after you call `installIoDispatcher(...)`. KiteCore does not ship a worker-backed dispatcher. |
| `WeakRef` | It holds strongly on Wasm and on JS runtimes older than ES2021. Check `WeakRef.isWeakSupported`. |
| `Platform` | The strings come from the host and can be empty. Do not parse them for version checks. |
| `String.format` | No `%t` date and time conversions, and no locale other than the neutral one. |
| API stability | Explicit API mode and a committed ABI dump make every signature change visible. They do not make the API stable before 1.0. |

</details>

## How it is tested

107 tests run on eight targets: the JVM, the Android host, the iOS simulator, macOS, and
JS and Wasm in both the browser and Node. Every public function and class has a test.

`String.format` is checked against `java.util.Formatter` itself. A seeded test compares
60,000 random patterns on every JVM run. Before 0.2.0, a longer run compared about 2.3
million patterns and found no difference.

The cancellation tests cover the cases that often go wrong: a timeout inside the block, a
caller that is cancelled while it waits, and a block that calls its own `SingleFlight` key.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). A change to the public API also needs a new ABI
dump. Make it with `./gradlew :kitecore:updateKotlinAbi`. Every change comes with tests.
Run them on the web targets too, because most subtle bugs show up there.

## License

Apache-2.0. See [NOTICE](NOTICE).

Part of the Kite family: [KitePDF](https://github.com/yuroyami/KitePDF),
[KiteImageCodec](https://github.com/yuroyami/KiteImageCodec),
[KiteQR](https://github.com/yuroyami/KiteQR).
