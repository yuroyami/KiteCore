# Coroutine utilities

Suspend-function helpers from the `io.github.yuroyami.kitecore.coroutines` package: retrying, safe error capture, timeouts and racing, parallel collection operations, launch helpers, and synchronization primitives. Everything on this page is common code and depends only on kotlinx.coroutines. Each snippet is copy-pasteable and compiles against the real API.

```kotlin
import io.github.yuroyami.kitecore.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
```

## Retrying

### Fixed-delay attempts with retry

```kotlin
val config = retry(times = 3, delayBetween = 250.milliseconds) { attempt ->
    println("attempt $attempt")   // 1, then 2, then 3
    api.fetchConfig()
}
// Returns the first successful result. The failure of attempt 3 is rethrown
// unchanged.
```

The attempt number starts at 1. Cancellation: a `CancellationException` thrown by the block or while delaying is rethrown immediately and is never retried.

### Exponential delays with retryWithBackoff

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

### Unbounded attempts with retryForever

```kotlin
val connection = retryForever(delayBetween = 5.seconds) { attempt ->
    println("connecting, attempt $attempt")
    connectToBroker()
}
// Loops until connectToBroker() succeeds.
```

The attempt number saturates at `Int.MAX_VALUE`. A failure that `retryIf` rejects is rethrown unchanged and ends the loop. Cancellation: a `CancellationException` is rethrown immediately and never passed to `retryIf`; cancelling the calling coroutine is the only way to stop an endlessly failing loop, so give the caller a scope you can cancel.

### Redundant attempts with firstSuccessOf

```kotlin
val bytes = firstSuccessOf(
    { cdn.download(assetId) },
    { mirror.download(assetId) },
    { origin.download(assetId) },
)
// All three downloads start concurrently. The first success wins and cancels
// the rest. A failing block does not decide the race; the others keep running.
// If every block fails, the last observed failure is rethrown.
```

Cancellation: cancelling the calling coroutine cancels every block. A block that throws `CancellationException` counts as failed for the race, but the cancellation is not swallowed inside that block's coroutine.

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

## Timeouts and racing

### withTimeoutOrDefault

```kotlin
val suggestions = withTimeoutOrDefault(200.milliseconds, default = emptyList()) {
    searchService.suggest(query)
}
// Runs for at most 200 ms. On timeout the block is cancelled and emptyList()
// is returned.
```

A non-positive timeout returns the default without running the block. Failures thrown by the block propagate unchanged; the default signals a timeout, nothing else. Cancellation: cancellation of the calling coroutine is rethrown and is never converted into the default.

### raceOf

```kotlin
val page = raceOf(
    { cache.read(url) },
    { network.fetch(url) },
)
// Both blocks start concurrently; the first one to complete wins and the
// loser is cancelled. If the first block to complete failed, that failure is
// rethrown and the rest are cancelled.
```

Use `raceOf` when the first outcome decides, success or failure; use `firstSuccessOf` when losers should keep trying after one contender fails. Cancellation: cancelling the calling coroutine cancels every block; a winner is never cancelled by this function.

### awaitOrNull and awaitOrDefault

```kotlin
import kotlinx.coroutines.async

val prefetch = scope.async { renderThumbnail(document) }

val thumbnail = prefetch.awaitOrNull(100.milliseconds)
// null means the 100 ms elapsed first. A failed deferred rethrows its failure;
// null signals only a timeout.

val banner = prefetch.awaitOrDefault(100.milliseconds, default = placeholderImage)
// Same contract with a fallback value instead of null.
```

A non-positive timeout returns `null` (or the default) without waiting. Cancellation: on timeout only the wait is cancelled, never the deferred itself, so the prefetch above keeps rendering and can be awaited again later. Cancellation of the calling coroutine, and cancellation of the deferred, are rethrown.

## Parallel collections

All `parallel*` functions share one contract:

- At most `concurrency` blocks run at the same time, limited by a `Semaphore`. The default is unbounded.
- Results keep the original iteration order, regardless of completion order.
- The first failing block cancels the remaining work and its failure is rethrown to the caller.
- Cancellation: cancelling the calling coroutine cancels every in-flight block before rethrowing.

### parallelMap and parallelMapIndexed

```kotlin
val profiles = userIds.parallelMap(concurrency = 8) { id ->
    api.fetchProfile(id)
}
// profiles[i] corresponds to userIds[i], whatever order the fetches finish in.

val checksums = files.parallelMapIndexed(concurrency = 4) { index, file ->
    "$index:${sha256(file)}"
}
```

### parallelMapNotNull and parallelMapIndexedNotNull

```kotlin
val avatars = userIds.parallelMapNotNull(concurrency = 8) { id ->
    api.fetchAvatarOrNull(id)
}
// Null results are removed; the survivors keep their original relative order.
```

### parallelFlatMap

```kotlin
val allItems = pageUrls.parallelFlatMap(concurrency = 4) { url ->
    fetchPage(url).items
}
// Pages are fetched concurrently; the items are flattened in page order.
```

### parallelForEach and parallelForEachIndexed

```kotlin
staleEntries.parallelForEach(concurrency = 16) { entry ->
    cache.evict(entry)
}
// Returns once every action has finished. The first failure cancels the rest.
```

### parallelFilter and parallelFilterNot

```kotlin
val reachable = hosts.parallelFilter(concurrency = 32) { host ->
    ping(host)
}
// Predicates run concurrently; matching hosts keep their original order.
// parallelFilterNot keeps the elements whose predicate returned false.
```

### awaitAllSettled

`awaitAll` throws on the first failed deferred and abandons the rest. `awaitAllSettled` awaits every deferred and reports each outcome:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

val outcomes: List<Result<Report>> = supervisorScope {
    regions
        .map { region -> async { buildReport(region) } }
        .awaitAllSettled()
}
outcomes.forEachIndexed { index, outcome ->
    outcome
        .onSuccess { publish(it) }
        .onFailure { println("${regions[index]} failed: $it") }
}
// One Result per deferred, in the original order. A failed report does not
// prevent the others from being awaited.
```

Pair it with `supervisorScope`: under a plain `coroutineScope`, a failing `async` child still cancels its siblings before the results can be settled. Cancellation: cancelling the calling coroutine rethrows its `CancellationException`; only cancellation that belongs to one of the deferred values is captured as a failed `Result`.

## Launch helpers

### launchPeriodic

```kotlin
import kotlin.time.Duration.Companion.minutes

val sync = scope.launchPeriodic(interval = 15.minutes, initialDelay = 1.minutes) {
    syncBookmarks()
}
// First run after 1 min. Each following run starts 15 min after the previous
// run finished: a fixed pause, not a fixed rate.

sync.cancel()
// Stops the loop at the next suspension point.
```

A failure thrown by the action stops the loop and propagates through the returned `Job` to the scope. Combine with `runCatchingCancellable` inside the action when one failed run should not end the schedule.

### launchCatching

```kotlin
scope.launchCatching(onError = { cause -> log("refresh failed", cause) }) {
    refreshFeed()
}
// Non-cancellation failures go to onError and the Job completes normally,
// so one failed refresh does not tear down the scope.
```

Cancellation: a `CancellationException` is rethrown, never passed to `onError`, so cancelling the returned `Job` or the scope behaves like a plain `launch`. A failure thrown by `onError` itself propagates to the scope.

### launchDelayed

```kotlin
val hint = scope.launchDelayed(3.seconds) { showHint() }

// User acted before the hint was due:
hint.cancel()
// Cancelling during the delay skips showHint() entirely.
```

## Synchronization primitives

### Skip contended work: Mutex.tryWithLock

A frame renderer where a repaint request during an ongoing repaint should be skipped, not queued:

```kotlin
import kotlinx.coroutines.sync.Mutex

val renderLock = Mutex()

fun requestRepaint() {
    val done = renderLock.tryWithLock { repaintNow() }
    if (done == null) {
        // Another repaint holds the lock; drop this request.
    }
}
```

`tryWithLock` never waits: `null` means the mutex was held by someone else. The mutex is released even when the block throws. Cancellation: this function does not suspend and performs no cancellation checks; a non-suspending block runs to completion once started.

### Wait with a deadline: Mutex.withLockOrNull

A shutdown hook that flushes pending writes, but not at any price:

```kotlin
val writeLock = Mutex()

suspend fun flushOnShutdown(): Boolean {
    val flushed = writeLock.withLockOrNull(500.milliseconds) { flushWrites() }
    return flushed != null   // false: a writer held the lock for the full 500 ms
}
```

A non-positive timeout degrades to a single `tryLock` attempt. Once the lock is acquired, the block runs without any further deadline. Cancellation: waiting for the lock is cancellable and rethrows without leaving the mutex locked; cancellation inside the block releases the mutex and rethrows.

### Permit-based variants: Semaphore.tryWithPermit and Semaphore.withPermitOrNull

A connection pool with four slots:

```kotlin
import kotlinx.coroutines.sync.Semaphore

val connections = Semaphore(permits = 4)

suspend fun query(sql: String): Rows? =
    connections.withPermitOrNull(1.seconds) { runQuery(sql) }
// null when all four connections stayed busy for a full second.

fun quickProbe(): PoolStats? = connections.tryWithPermit { readStats() }
// Non-suspending; null when no permit was free at this instant.
```

Same shape as the mutex variants: the permit is released even when the block throws, waiting is cancellable without consuming a permit, and cancellation inside the block releases the permit and rethrows.

### Per-key mutual exclusion: KeyedMutex

One writer per user journal, with different users fully independent:

```kotlin
val journalLocks = KeyedMutex<String>()

suspend fun appendToJournal(userId: String, line: String) {
    journalLocks.withLock(userId) {
        val journal = openJournal(userId)
        journal.append(line)   // never interleaves with another writer for userId
    }
}
// Two calls with equal keys serialize; calls with different keys run in
// parallel. Keys need stable equals and hashCode.
```

Entries are created on demand and removed as soon as the last interested caller leaves, so unused keys hold no memory; a quiescent instance reports `activeKeyCount() == 0`. `withLockOrNull(key, timeout)` bounds the wait and returns `null` on timeout, and `isLocked(key)` is a snapshot that may change immediately after it returns. Cancellation: waiting for the key's mutex is cancellable and rethrows; cancellation inside the block releases the mutex and rethrows; entry cleanup runs in a `NonCancellable` context, so a cancelled caller never leaks its entry.

### Deduplicate concurrent work: SingleFlight

Several screens request the same profile at once; only one network call should happen:

```kotlin
val profileFlights = SingleFlight<String, Profile>()

suspend fun profile(id: String): Profile =
    profileFlights.run(id) { api.fetchProfile(id) }
// Three concurrent profile("42") calls produce one fetchProfile execution.
// All three receive the same Profile, or the same failure thrown as is.
```

The first caller for a key runs the block in its own context; callers that arrive while that execution is in flight only wait. When the execution finishes the key is released, so the next caller starts a fresh one and completed keys hold no memory. `isInFlight(key)` and `inFlightKeyCount()` are snapshots. Cancellation: cancelling the executing caller cancels the shared execution, but joining callers do not inherit that cancellation; they race for a fresh execution instead. Cancelling a joining caller only stops its wait and rethrows. Flight cleanup runs in a `NonCancellable` context, so a cancelled execution never leaks its key.

### Trailing-edge scheduling: Debouncer

Search-as-you-type, where only the query the user settles on should hit the backend:

```kotlin
class SearchController(scope: CoroutineScope) {
    private val debouncer = Debouncer(window = 300.milliseconds, scope = scope)

    fun onQueryChanged(query: String) {
        debouncer.submit { showResults(search(query)) }
    }
    // Each keystroke replaces the pending block and restarts the 300 ms window.
    // Only the block that stays newest for 300 ms runs, as a new child
    // coroutine of the scope.

    fun onScreenHidden() {
        debouncer.cancel()
        // Discards the pending block; the debouncer keeps accepting new ones.
    }
}
```

`submit` and `cancel` never suspend and never block; they funnel through a conflated channel, which makes them safe to call from any thread. A block whose window already elapsed is not affected by `cancel`. Failures of a block follow the scope's regular exception handling. Cancellation: a debouncer whose scope is cancelled stops permanently, `isActive` becomes `false`, and later submissions are dropped.

### Leading-edge admission: Throttler

A refresh button that must not hammer the backend:

```kotlin
val refreshThrottler = Throttler(window = 10.seconds)

suspend fun onRefreshClicked() {
    val refreshed = refreshThrottler.run { reloadDashboard() }
    if (refreshed == null) showSnackbar("Refreshed a moment ago")
}
// The first click runs reloadDashboard() and returns its value. Clicks within
// the next 10 s return null without running anything.
```

The window starts when a permitted block begins, and a failed or cancelled block still consumes it. The block runs outside the internal lock, so concurrent callers are rejected instead of queued. `isThrottled` is a snapshot, and `reset()` closes the current window so the next `run` is permitted. The clock is injectable, which makes the class testable with a virtual time source:

```kotlin
import kotlin.time.TestTimeSource

val clock = TestTimeSource()
val throttler = Throttler(window = 5.seconds, timeSource = clock)

throttler.run { "first" }    // "first"
throttler.run { "second" }   // null: the window is still open
clock += 5.seconds
throttler.run { "third" }    // "third": the window elapsed
```

Cancellation: waiting for the internal lock is cancellable and rethrows; cancellation inside the block rethrows after the window has started, so a cancelled run still throttles the next caller.

### One-time async initialization: suspendLazy

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
| `awaitAllSettled()` | Awaits every deferred and returns each outcome as a `Result`, in order, without throwing for failed values |
| `awaitOrDefault(timeout, default)` | Awaits a deferred, returning a default when the timeout elapses first; the deferred keeps running |
| `awaitOrNull(timeout)` | Awaits a deferred, returning `null` when the timeout elapses first; the deferred keeps running |
| `Debouncer(window, scope)` | Runs only the last block submitted within a window; members `window`, `isActive`, `submit`, `cancel` |
| `firstSuccessOf(vararg blocks)` | Concurrent race won by the first success; failures do not decide it |
| `KeyedMutex<K>` | Independent mutex per key; members `withLock`, `withLockOrNull`, `isLocked`, `activeKeyCount` |
| `launchCatching(onError, block)` | `launch` that routes non-cancellation failures to a handler instead of the scope |
| `launchDelayed(delay, block)` | `launch` that runs the block after a delay; cancelling during the delay skips it |
| `launchPeriodic(interval, initialDelay, action)` | `launch` that repeats an action with a fixed pause between runs |
| `parallelFilter(concurrency, predicate)` | Concurrent filter preserving order |
| `parallelFilterNot(concurrency, predicate)` | Concurrent inverse filter preserving order |
| `parallelFlatMap(concurrency, transform)` | Concurrent map flattened in original order |
| `parallelForEach(concurrency, action)` | Concurrent action per element; returns when all finished |
| `parallelForEachIndexed(concurrency, action)` | Concurrent indexed action per element |
| `parallelMap(concurrency, transform)` | Concurrent map preserving order |
| `parallelMapIndexed(concurrency, transform)` | Concurrent indexed map preserving order |
| `parallelMapIndexedNotNull(concurrency, transform)` | Concurrent indexed map dropping `null` results |
| `parallelMapNotNull(concurrency, transform)` | Concurrent map dropping `null` results |
| `raceOf(vararg blocks)` | Concurrent race won by the first completion, success or failure |
| `retry(times, delayBetween, block)` | Fixed-delay attempts; final failure rethrown; cancellation never retried |
| `retryForever(delayBetween, retryIf, block)` | Retries until success; stopped only by cancellation or a rejected failure |
| `retryWithBackoff(times, initialDelay, factor, maxDelay, retryIf, block)` | Exponential-delay attempts with a failure filter |
| `runCatchingCancellable(block)` | `runCatching` that rethrows `CancellationException`; plain and receiver forms |
| `SingleFlight<K, T>` | Shares one in-flight execution per key; members `run`, `isInFlight`, `inFlightKeyCount` |
| `SuspendLazy<T>` / `suspendLazy(initializer)` | Value initialized on first `get`, shared across concurrent callers; members `get`, `valueOrNull`, `isInitialized` |
| `Throttler(window, timeSource)` | Admits at most one run per window; members `window`, `isThrottled`, `run`, `reset` |
| `tryWithLock(block)` | Runs under a `Mutex` only when it is free, `null` otherwise; never waits |
| `tryWithPermit(block)` | Runs under a `Semaphore` permit only when one is free, `null` otherwise; never waits |
| `withLockOrNull(timeout, block)` | Runs under a `Mutex`, waiting at most the timeout for the lock, `null` on timeout |
| `withPermitOrNull(timeout, block)` | Runs under a `Semaphore` permit, waiting at most the timeout, `null` on timeout |
| `withTimeoutOrDefault(timeout, default, block)` | `withTimeout` that returns a default instead of throwing |
