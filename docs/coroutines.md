# Coroutine utilities

Suspend-function helpers from the `io.github.yuroyami.kitecore.coroutines` package: retry with backoff, safe error capture, bounded parallel work, shared in-flight calls, and lazy values that suspend. Everything on this page is common code and depends only on kotlinx.coroutines. Each snippet is copy-pasteable and compiles against the real API.

```kotlin
import io.github.yuroyami.kitecore.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
```

## Retrying with retryWithBackoff

```kotlin
val user = retryWithBackoff(
    times = 5,
    initialDelay = 100.milliseconds,
    factor = 2.0,
    maxDelay = 2.seconds,
    retryIf = { it !is IllegalArgumentException },
) { attempt ->
    api.fetchUser(userId)
}
// Pauses between the 5 attempts: 100 ms, 200 ms, 400 ms, 800 ms.
// Every pause is capped at maxDelay.
```

The first pause is `initialDelay`; every following pause is the previous one multiplied by `factor` and capped at `maxDelay`. A failure that `retryIf` rejects, and the failure of the final attempt, are rethrown unchanged. Cancellation is rethrown immediately, never retried, and never passed to `retryIf`.

For a fixed pause between attempts, pass `factor = 1.0`.

## Safe error capture

### Why plain runCatching is unsafe in suspend code

`runCatching` catches every `Throwable`, and that includes `CancellationException`. In suspend code, cancellation is delivered as a `CancellationException` thrown from a suspension point. Capturing it in a `Result` means the coroutine does not stop when its scope is cancelled: loops keep looping, the code after the catch keeps running, and failure branches fire for a screen the user already left. Structured concurrency relies on that exception reaching the coroutine machinery.

```kotlin
// Unsafe: a cancelled coroutine lands in the onFailure branch and keeps going.
val result = runCatching { api.load() }

// Safe: cancellation is rethrown; only ordinary failures become Result.failure.
val result = runCatchingCancellable { api.load() }
```

### runCatchingCancellable

```kotlin
suspend fun loadDashboard(): DashboardState {
    val result = runCatchingCancellable { api.fetchDashboard() }
    return result.fold(
        onSuccess = { DashboardState.Content(it) },
        onFailure = { DashboardState.Error(it) },
    )
}
// Ordinary failures become Result.failure. If the calling coroutine is
// cancelled while fetchDashboard() suspends, the CancellationException
// propagates and this function never returns a Result.
```

The receiver form mirrors the receiver form of `runCatching`:

```kotlin
val parsed: Result<Config> = rawJson.runCatchingCancellable { parseConfig(this) }
// this inside the block is rawJson.
```

## Parallel collections

`parallelMap` and `parallelForEach` share one contract:

- At most `concurrency` blocks run at the same time, limited by a `Semaphore`. The default is unbounded.
- `parallelMap` keeps the original iteration order, regardless of completion order.
- The first failing block cancels the remaining work and its failure is rethrown to the caller.
- Cancellation: cancelling the calling coroutine cancels every in-flight block before rethrowing.

### parallelMap

```kotlin
val profiles = userIds.parallelMap(concurrency = 8) { id ->
    api.fetchProfile(id)
}
// profiles[i] corresponds to userIds[i], whatever order the fetches finish in.
```

### parallelForEach

```kotlin
staleEntries.parallelForEach(concurrency = 16) { entry ->
    cache.evict(entry)
}
// Returns once every action has finished. The first failure cancels the rest.
```

## Deduplicate concurrent work: SingleFlight

Several screens request the same profile at once; only one network call should happen:

```kotlin
val profileFlights = SingleFlight<String, Profile>()

suspend fun profile(id: String): Profile =
    profileFlights.run(id) { api.fetchProfile(id) }
// Three concurrent profile("42") calls produce one fetchProfile execution.
// All three receive the same Profile, or the same failure thrown as is.
```

The first caller for a key runs the block in its own context; callers that arrive while that execution is in flight only wait. When the execution finishes the key is released, so the next caller starts a fresh one and completed keys hold no memory. `isInFlight(key)` and `inFlightKeyCount()` are snapshots. Cancellation: cancelling the executing caller cancels the shared execution, but joining callers do not inherit that cancellation; they race for a fresh execution instead. Cancelling a joining caller only stops its wait and rethrows. Flight cleanup runs in a `NonCancellable` context, so a cancelled execution never leaks its key.

## One-time async initialization: suspendLazy

A database handle that should be opened once, on first use, from whichever coroutine gets there first:

```kotlin
val database = suspendLazy { openDatabase("app.db") }

suspend fun records(): List<Record> = database.get().queryAll()
// The first get() runs openDatabase; concurrent first callers share that one
// initialization, with one caller running it and the others waiting for the
// outcome. After success, every later get() returns without suspending.
```

A failed initialization is not cached: the failure propagates to the caller that ran it, and the next `get()` runs the initializer again. `isInitialized` and `valueOrNull()` never suspend; for a nullable `T`, a stored `null` is indistinguishable from the uninitialized state through `valueOrNull()`, so check `isInitialized` to tell them apart. Cancellation: cancelling the initializing caller stops the initializer and leaves the value uninitialized, so the next caller retries; cancelling a waiting caller only stops its wait and rethrows.

## Reference

Every public declaration in `io.github.yuroyami.kitecore.coroutines`, alphabetically. Overloads share a row; class rows list their public members.

| Declaration | Summary |
|---|---|
| `parallelForEach(concurrency, action)` | Concurrent action per element; returns when all finished |
| `parallelMap(concurrency, transform)` | Concurrent map preserving order |
| `retryWithBackoff(times, initialDelay, factor, maxDelay, retryIf, block)` | Exponential-delay attempts with a failure filter |
| `runCatchingCancellable(block)` | `runCatching` that rethrows `CancellationException`; plain and receiver forms |
| `SingleFlight<K, T>` | Shares one in-flight execution per key; members `run`, `isInFlight`, `inFlightKeyCount` |
| `SuspendLazy<T>` / `suspendLazy(initializer)` | Value initialized on first `get`, shared across concurrent callers; members `get`, `valueOrNull`, `isInitialized` |
