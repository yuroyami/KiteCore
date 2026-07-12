# IO dispatcher

`ioDispatcher` is a top-level `CoroutineDispatcher` for blocking and IO-bound work in shared code. On targets with real threads it resolves to `Dispatchers.IO`; on the web it resolves to a dispatcher you install, with a warned fallback when you do not.

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher
```

## Know what it resolves to

| Target | `ioDispatcher` resolves to |
|---|---|
| JVM | `Dispatchers.IO` |
| Android | `Dispatchers.IO` |
| iOS, macOS | `Dispatchers.IO` (elastic up to 64 threads on Kotlin/Native) |
| js | the dispatcher passed to `installIoDispatcher`, otherwise `Dispatchers.Default` with a one-time warning |
| wasmJs | the dispatcher passed to `installIoDispatcher`, otherwise `Dispatchers.Default` with a one-time warning |

## Move blocking work off the caller

Use it exactly like `Dispatchers.IO` in a `withContext` block:

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher
import kotlinx.coroutines.withContext

suspend fun readSettings(path: String): Settings = withContext(ioDispatcher) {
    val bytes = readFileBytes(path)  // blocking call, off the caller's thread
    parseSettings(bytes)
}
```

On JVM, Android, and Apple targets the block runs on an IO pool thread. On js and wasmJs it runs wherever `ioDispatcher` currently points, which is why the next section matters.

## Install a dispatcher on web

No OS thread pool exists on js and wasmJs. `installIoDispatcher` sets the dispatcher that `ioDispatcher` resolves to on those targets:

```kotlin
import io.github.yuroyami.kitecore.installIoDispatcher

fun main() {
    installIoDispatcher(workerBackedDispatcher)
    startApp()
}
```

Rules:

- Call it once at startup, before the first read of `ioDispatcher`.
- A later call replaces the previously installed dispatcher.
- On JVM, Android, and Apple targets the call is a no-op, because `ioDispatcher` is already backed by a real thread pool. It is safe to call unconditionally from common code.

!!! tip
    kmp-ssot can generate a Worker-backed dispatcher for you: enable `web { generateIoWorker = true }` and pass the generated dispatcher to `installIoDispatcher`.

## Understand the fallback warning

If nothing was installed on js or wasmJs, the first read of `ioDispatcher` prints one console warning per program run and returns `Dispatchers.Default`, the single-threaded JS event loop:

```
[KiteCore] ioDispatcher: no Web Worker dispatcher installed; falling back to
Dispatchers.Default (single-threaded JS event loop). Heavy/blocking work will
not leave the main thread. ...
```

The fallback is functional: coroutines still run, suspend, and resume correctly. What it cannot do is move CPU-heavy or blocking work off the main thread, so long computations will freeze the UI. To fix that, either install a Worker-backed dispatcher as shown above, or hand the specific job to [`WebWorker` / `runInWorker`](webworker.md).

The warning prints once, on the first fallback read. Installing a dispatcher afterwards still works: the next read resolves to it.
