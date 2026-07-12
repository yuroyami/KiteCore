# Web Workers

`WebWorker` runs a self-contained JavaScript job off the main thread on the web targets. It exists on Kotlin/JS and Kotlin/Wasm with an identical API; the other targets have real threads and use [`ioDispatcher`](io-dispatcher.md) instead.

| Target | `WebWorker`, `runInWorker` |
|---|---|
| js | available (declared in `jsMain`) |
| wasmJs | available (declared in `wasmJsMain`) |
| JVM, Android, Apple | not present; use `ioDispatcher` |

The worker is created at runtime from a Blob URL, so there is no separate script file to bundle. The job runs in the Worker scope with no access to your Kotlin module: everything it needs must arrive through the payload string.

## Run a one-shot job

`runInWorker(jobJs, payload)` builds a worker, runs the job once, and closes the worker:

```kotlin
import io.github.yuroyami.kitecore.runInWorker

suspend fun sha256Hex(text: String): String = runInWorker(
    jobJs = """async (payload) => {
        const bytes = new TextEncoder().encode(payload)
        const digest = await crypto.subtle.digest('SHA-256', bytes)
        return Array.from(new Uint8Array(digest))
            .map(b => b.toString(16).padStart(2, '0')).join('')
    }""",
    payload = text,
)
```

`jobJs` is a JavaScript function expression `(payload) => result`, synchronous or asynchronous. The worker awaits the result and normalizes it to a string before replying.

## Reuse a worker across calls

Creating a worker costs a script compile and a thread spawn. For repeated jobs, build one with `WebWorker.of` and keep it:

```kotlin
import io.github.yuroyami.kitecore.WebWorker

val primeWorker = WebWorker.of(
    """(payload) => {
        const n = Number(payload)
        let count = 0
        for (let i = 2; i <= n; i++) {
            let prime = true
            for (let j = 2; j * j <= i; j++) if (i % j === 0) { prime = false; break }
            if (prime) count++
        }
        return count
    }"""
)

suspend fun primesBelow(n: Int): Int = primeWorker.call(n.toString()).toInt()

fun shutdown() = primeWorker.close()
```

Calls on one instance are serialized, one request/reply in flight per worker, so `call` is safe to invoke from concurrent coroutines; they queue. `close()` terminates the underlying Worker and is idempotent.

## Pass structured data

The contract is string in, string out. For anything structured, JSON-encode both directions:

```kotlin
val resizeWorker = WebWorker.of(
    """(payload) => {
        const opts = JSON.parse(payload)
        const area = opts.width * opts.height
        return JSON.stringify({ area: area, budget: Math.round(area * opts.quality) })
    }"""
)

val reply = resizeWorker.call("""{"width":1920,"height":1080,"quality":0.8}""")
```

Decode the returned string with your JSON library of choice. Keep payloads modest; each one is copied into the worker.

## Cancel and time out

Every request carries a correlation id, and a reply to a cancelled call is dropped instead of being delivered to the next caller. Cancelling the coroutine that awaits `call` is therefore safe, and `withTimeout` composes directly:

```kotlin
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout

val reply = withTimeout(5.seconds) { worker.call(payload) }
```

Cancellation abandons the reply; it does not interrupt the JavaScript, so the worker finishes its current job in the background. `close()` is the way to stop a runaway worker.

## Handle failures

Failures surface from `call` as `WebWorkerException`, with one exception for use-after-close:

| Failure | What `call` throws |
|---|---|
| The job throws | `WebWorkerException` carrying the error, with stack when available |
| The worker script fails to load, e.g. a syntax error in `jobJs` | `WebWorkerException`, for the in-flight call and every later one |
| `close()` while a call is in flight | `WebWorkerException` on that call |
| `call()` on a closed worker | `IllegalStateException` |

```kotlin
import io.github.yuroyami.kitecore.WebWorkerException

val outcome = try {
    worker.call(payload)
} catch (e: WebWorkerException) {
    computeOnMainThread(payload)  // fallback
}
```

A load error is sticky: once the script has failed, the worker cannot recover. `close()` it and build a new one with `WebWorker.of`.
