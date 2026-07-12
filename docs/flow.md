# Flow utilities

Operators, builders, and StateFlow helpers from the `io.github.yuroyami.kitecore.flow` package. Everything on this page is common code: it runs on every KiteCore target and depends only on kotlinx.coroutines. Each snippet is copy-pasteable and compiles against the real API.

```kotlin
import io.github.yuroyami.kitecore.flow.*
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
```

## Rate limiting

### Ignore rapid repeats with throttleFirst

```kotlin
buttonClicks
    .throttleFirst(500.milliseconds)
    .collect { submitOrder() }
// The first click emits immediately. Clicks arriving within 500 ms of an
// emitted click are dropped. The window reopens 500 ms after each emission.
```

Dropped values are lost, not conflated: after the window reopens, the next value to arrive is emitted, not the last one that was dropped. Cancelling the collector cancels the upstream and the internal timer.

### Sample the newest value with throttleLatest

```kotlin
scrollOffsets
    .throttleLatest(100.milliseconds)
    .collect { renderMinimap(it) }
// The first offset emits immediately. While the 100 ms window is open,
// newer offsets replace each other; only the latest one emits when it elapses.
```

`throttleFirst` keeps the oldest value in each window, `throttleLatest` keeps the newest. Use `throttleFirst` for actions (button taps), `throttleLatest` for state you render (scroll positions, progress).

## Windowing and batching

### Sliding windows with windowed

```kotlin
flowOf(1, 2, 3, 4, 5)
    .windowed(size = 3, step = 1)
    .collect { println(it) }
// [1, 2, 3]
// [2, 3, 4]
// [3, 4, 5]
```

A window is emitted only when it is full; a trailing partial window is dropped. When `step` is greater than `size`, the elements between windows are skipped:

```kotlin
flowOf(1, 2, 3, 4, 5, 6, 7, 8)
    .windowed(size = 2, step = 3)
    .collect { println(it) }
// [1, 2]
// [4, 5]
// [7, 8]
```

### Fixed-size chunks

```kotlin
flowOf(1, 2, 3, 4, 5)
    .chunked(2)
    .collect { println(it) }
// [1, 2]
// [3, 4]
// [5]      <- the final partial chunk is emitted on completion
```

### Time-bounded chunks

```kotlin
telemetryEvents
    .chunked(window = 500.milliseconds, maxSize = 100)
    .collect { batch -> upload(batch) }
// A chunk opens when its first event arrives and is emitted 500 ms later,
// or as soon as it holds 100 events, whichever happens first.
```

Empty lists are never emitted, and a non-empty chunk in progress is emitted when the upstream completes. The upstream is collected without conflation, so values wait for a slow collector instead of being lost. The two `chunked` overloads are distinguished by argument type: `chunked(100)` batches by count, `chunked(500.milliseconds)` batches by time.

## Pairing

### Track the previous value with withPrevious

```kotlin
flowOf("a", "b", "c")
    .withPrevious()
    .collect { (previous, current) -> println("$previous -> $current") }
// null -> a
// a -> b
// b -> c
```

The first pair carries `null` as the previous value, so every upstream value reaches the collector.

### Consecutive pairs with pairwise

```kotlin
flowOf(3, 7, 12)
    .pairwise()
    .collect { println(it) }
// (3, 7)
// (7, 12)
```

The transform overload computes a value from each pair directly:

```kotlin
temperatureReadings
    .pairwise { previous, current -> current - previous }
    .collect { delta -> println("changed by $delta") }
// Emits the difference between each reading and the one before it.
```

Flows with fewer than two values produce nothing. Use `withPrevious` when the first value matters, `pairwise` when only complete pairs do.

## Concurrency

### mapAsync preserves upstream order

```kotlin
import kotlinx.coroutines.flow.asFlow

userIds.asFlow()
    .mapAsync(concurrency = 4) { id -> api.fetchUser(id) }
    .collect { user -> println(user.name) }
// Up to 4 fetches run at once; results arrive in userIds order.
```

A result is emitted only after the results of all earlier values have been emitted, so one slow transform delays later results. The upstream is collected only while permits are available, which bounds memory at `concurrency` pending results. A failure in the transform or the upstream cancels all in-flight work and fails the flow.

### mapAsyncUnordered emits in completion order

```kotlin
imageUrls.asFlow()
    .mapAsyncUnordered(concurrency = 4) { url -> downloadThumbnail(url) }
    .collect { thumbnail -> cache.put(thumbnail) }
// Each thumbnail is emitted as soon as it is ready; output order can differ
// from upstream order.
```

Same concurrency bound and failure behavior as `mapAsync`. Prefer it when downstream work does not care about order, because no result waits behind a slower one.

## Resilience

### retryWithBackoff

```kotlin
priceUpdates
    .retryWithBackoff(
        retries = 5,
        initialDelay = 200.milliseconds,
        factor = 2.0,
        maxDelay = 10.seconds,
        retryIf = { it !is IllegalArgumentException },
    )
    .collect { render(it) }
// On failure the flow is re-collected after 200 ms, then 400 ms, 800 ms,
// 1.6 s, 3.2 s. Each delay is capped at maxDelay.
```

The delay before retry `n` (0-based) is `initialDelay * factor.pow(n)`, capped at `maxDelay`. A failure is retried only while `retryIf` returns `true` for it. `CancellationException` is never retried, so cancelling the collector stops the flow immediately. Once retries are exhausted, the last failure is rethrown to the collector.

### asResult, filterSuccess, filterFailure

```kotlin
locationUpdates
    .asResult()
    .collect { result ->
        result
            .onSuccess { location -> moveMarker(location) }
            .onFailure { cause -> showBanner(cause) }
    }
// Every value arrives as Result.success. A terminal upstream failure arrives
// as a single Result.failure, then the flow completes normally.
```

Collectors never observe an exception from the upstream. Cancellation is not wrapped and propagates as usual. Split a `Flow<Result<T>>` back into its parts with the two filters:

```kotlin
val results = locationUpdates.asResult()

results.filterSuccess()   // Flow<Location>: successful values only
results.filterFailure()   // Flow<Throwable>: failure causes only
```

### timeoutBetweenEmissions

```kotlin
heartbeats
    .timeoutBetweenEmissions(30.seconds)
    .collect { registerBeat(it) }
// Fails with TimeoutCancellationException when 30 s pass without a beat.
// The gap between the start of collection and the first beat counts too.
```

Values already emitted are unaffected. On timeout the upstream is cancelled and the exception is thrown to the collector, so wrap the flow in `retryWithBackoff` or `asResult` if a silent stall should trigger recovery instead of a crash.

## Composition

### Stop on a signal with takeUntil

```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow

val stop = MutableSharedFlow<Unit>()

intervalFlow(1.seconds)
    .takeUntil(stop)
    .collect { println(it) }
// 0, 1, 2, ... completes as soon as stop emits its first value.
```

If the signal completes without emitting, the upstream is mirrored to completion. Values are handed to the collector without buffering, so nothing is delivered after the signal fires. A failure in either flow fails the resulting flow.

### Prefix values with startWith

```kotlin
val screenState = remoteArticles()
    .map { articles -> UiState.Content(articles) }
    .startWith(UiState.Loading)
// Collectors see Loading first, then Content once the network answers.
```

The vararg overload prefixes several values in order:

```kotlin
flowOf(3).startWith(1, 2).collect { print(it) }
// 123
```

The prefix is emitted once per collection, before the upstream is collected.

### Sequence flows with concatWith

```kotlin
cachedResults
    .concatWith(networkResults)
    .collect { showRow(it) }
// All cached rows first, then network rows.
```

The second flow is collected only after the upstream completes normally; if the upstream fails or the collector is cancelled, it is never collected. The vararg overload chains several flows, and a failure anywhere stops the concatenation.

### Sample another flow with withLatestFrom

```kotlin
saveClicks
    .withLatestFrom(formState) { _, form -> form }
    .collect { form -> persist(form) }
// Each click captures the form as it was at click time. Clicks that arrive
// before formState has emitted are dropped.
```

Only the upstream drives emissions; new `formState` values alone produce nothing. The overload without a transform pairs the values:

```kotlin
saveClicks.withLatestFrom(formState)   // Flow<Pair<Unit, FormState>>
```

### combine6 to combine9

`kotlinx.coroutines` stops at five flows; KiteCore continues to nine with the same semantics:

```kotlin
val uiState = combine6(
    user, cart, promotions, shipping, payment, connectivity,
) { u, c, promos, ship, pay, online ->
    CheckoutState(u, c, promos, ship, pay, online)
}
// Emits after every input has emitted at least once, then re-emits whenever
// any input produces a new value.
```

Bursts of updates may be conflated; the transform always observes the latest values. `combine7`, `combine8`, and `combine9` follow the same shape with more parameters.

### Flatten collections with flattenIterable

```kotlin
pagedResults               // Flow<List<Item>>
    .flattenIterable()     // Flow<Item>
    .collect { item -> println(item.id) }
// Every element of every page, in order. Empty pages produce nothing.
```

### Insert separators with intersperse

```kotlin
flowOf("a", "b", "c")
    .intersperse("|")
    .collect { print(it) }
// a|b|c
```

Flows with fewer than two values are emitted unchanged.

## Gating and time limits

### Skip early values with dropUntil

```kotlin
sensorReadings
    .dropUntil(calibrationDone)
    .collect { record(it) }
// Readings before calibrationDone emits are dropped; every reading after it
// is mirrored.
```

If the signal completes without emitting, every upstream value is dropped and the flow completes with the upstream. A failure in either flow fails the resulting flow.

### Duration shorthands: takeFor and dropFor

```kotlin
prices.takeFor(10.seconds)   // mirror the upstream for 10 s, then complete
prices.dropFor(10.seconds)   // drop values for 10 s, then mirror
```

These are shorthands for `takeUntil(timerFlow(duration))` and `dropUntil(timerFlow(duration))`.

### Include the terminating value with takeWhileInclusive

```kotlin
downloadProgress
    .takeWhileInclusive { it < 100 }
    .collect { println(it) }
// ... 95, 98, 100    <- unlike takeWhile, 100 is emitted before completion
```

The upstream is cancelled once the predicate fails.

## Builders

### intervalFlow

```kotlin
intervalFlow(period = 1.seconds, initialDelay = 5.seconds)
    .collect { tick -> println("tick $tick") }
// tick 0 after 5 s, then tick 1, tick 2, ... one per second.
```

The delay between emissions is measured from the completion of the previous emission, so a slow collector stretches the schedule instead of piling up values. The flow is infinite and completes only by cancellation.

### countdownFlow

```kotlin
countdownFlow(from = 3, interval = 1.seconds)
    .collect { println(it) }
// 3 (immediately), 2, 1, 0, then the flow completes.
```

### timerFlow

```kotlin
timerFlow(2.seconds)
    .collect { showTooltip() }
// A single Unit after 2 s, then completion.
```

### flowFromSuspend and deferFlow

```kotlin
val profile = flowFromSuspend { api.loadProfile() }
// Cold: each collection invokes loadProfile() again and emits its one result.

val feed = deferFlow { if (session.isLoggedIn()) remoteFeed() else localFeed() }
// Cold: the provider runs at collection time, so each collector can receive
// a different flow.
```

`flowFromSuspend` wraps a value-producing call, `deferFlow` wraps a flow-producing call.

### neverFlow

```kotlin
val idle: Flow<Nothing> = neverFlow()
// Never emits, never completes. Collection suspends until the collector is
// cancelled. Useful as an inert branch in a when expression that returns flows.
```

## Indexed operators

Each indexed operator restarts its counter at 0 for every collection.

```kotlin
flowOf("a", "b", "c")
    .mapIndexed { index, value -> "$index:$value" }
    .collect { print("$it ") }
// 0:a 1:b 2:c
```

```kotlin
flowOf(10, 20, 30, 40)
    .filterIndexed { index, _ -> index % 2 == 0 }
    .collect { print("$it ") }
// 10 30
// The index counts every upstream value, including the filtered ones.
```

```kotlin
uploads
    .onEachIndexed { index, file -> log("upload #$index: ${file.name}") }
    .collect { send(it) }
// Values pass through unchanged; the action sees each value with its position.
```

### Act on the first value with onFirst

```kotlin
messages
    .onFirst { hideLoadingSpinner() }
    .collect { append(it) }
// The action runs once, before the first value reaches the collector, and
// runs again on every new collection.
```

### Count emissions with runningCount

```kotlin
errors
    .runningCount()
    .collect { count -> badge.text = "$count" }
// 1, 2, 3, ... an empty upstream produces nothing.
```

### Discard values with mapToUnit

```kotlin
val refreshSignal: Flow<Unit> = pullGestures.mapToUnit()
// Keeps the events, drops the payloads.
```

## Terminal operators

These are suspend functions, not operators returning flows.

### firstOrDefault and lastOrDefault

```kotlin
val theme = settingsFlow.firstOrDefault(Theme.System)
// Returns the first value and cancels the upstream. The default is used only
// when the flow completes without emitting; a null first value is returned
// as is for nullable T.

val finalScore = scores.lastOrDefault(0)
// Collects the flow to completion and returns the last value, or 0 when empty.
```

### collectIn

```kotlin
val job = ticker.collectIn(scope) { render(it) }
// Shorthand for scope.launch { ticker.collect { render(it) } }.
```

Collection stops when the returned job or the scope is cancelled; an upstream failure fails the job through the scope.

## StateFlow utilities

### Derive state with mapState

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CartModel {
    private val items = MutableStateFlow<List<Item>>(emptyList())

    val total: StateFlow<Int> = items.mapState { list -> list.sumOf { it.price } }
}
// total.value is always in sync with items.value. No coroutine, no scope,
// nothing to cancel.
```

The view is not backed by a coroutine: reading `value` applies the transform to the source's current value, and collection maps the source with equality conflation. The transform must be fast and pure, because it runs on every value read and every source emission.

### Combine state with combineStates

```kotlin
val canSubmit: StateFlow<Boolean> =
    combineStates(emailValid, passwordValid) { email, password -> email && password }
// Overloads accept 2, 3, or 4 source StateFlows.
```

Same contract as `mapState`: no backing coroutine, and the transform must be fast and pure.

### Constant state with stateFlowOf

```kotlin
fun connectivity(): StateFlow<Boolean> = stateFlowOf(true)
// A StateFlow that always holds true. Collectors receive the value once and
// then suspend until cancelled. Useful as a fixed input where an API expects
// a StateFlow, for example in previews and tests.
```

### Atomic updates: toggle, increment, decrement, getAndSet

```kotlin
val muted = MutableStateFlow(false)
val nowMuted = muted.toggle()            // true; flips and returns the new value

val retries = MutableStateFlow(0)
retries.increment()                      // 1
retries.increment(by = 3)                // 4
retries.decrement()                      // 3

val bytesSent = MutableStateFlow(0L)
bytesSent.increment(by = 4096L)          // Long overloads mirror the Int ones

val selection = MutableStateFlow("none")
val previous = selection.getAndSet("all") // "none"; the swap is atomic
```

Every update is atomic with respect to concurrent updates: the function may be recomputed under contention but is applied exactly once. Collectors observe a new value only when it differs from the previous one, following `MutableStateFlow` equality conflation.

## Reference

Every public declaration in `io.github.yuroyami.kitecore.flow`, alphabetically. Overloads share a row.

| Declaration | Summary |
|---|---|
| `asResult()` | Wraps values in `Result.success` and one terminal failure in `Result.failure`, then completes normally |
| `chunked(size)` / `chunked(window, maxSize)` | Batches values into lists by count, or by time window with an optional size cap |
| `collectIn(scope, action)` | Launches collection in a scope and returns the `Job` |
| `combine6(...)` | Combines the latest values of six flows |
| `combine7(...)` | Combines the latest values of seven flows |
| `combine8(...)` | Combines the latest values of eight flows |
| `combine9(...)` | Combines the latest values of nine flows |
| `combineStates(a, b[, c[, d]], transform)` | Coroutine-free combined `StateFlow` view of 2 to 4 sources |
| `concatWith(other)` / `concatWith(vararg)` | Appends other flows after normal completion of the upstream |
| `countdownFlow(from, interval)` | Counts down from a number to 0, then completes |
| `decrement(by)` | Atomically subtracts from a `MutableStateFlow<Int>` or `<Long>` and returns the new value |
| `deferFlow(provider)` | Obtains the actual flow from a suspending provider at collection time |
| `dropFor(duration)` | Drops values for a duration, then mirrors the upstream |
| `dropUntil(signal)` | Drops values until a signal flow emits, then mirrors the upstream |
| `filterFailure()` | Emits the exception of every failed `Result`, drops successes |
| `filterIndexed(predicate)` | Filters with access to each value's 0-based position |
| `filterSuccess()` | Emits the value of every successful `Result`, drops failures |
| `firstOrDefault(default)` | First value, or the default when the flow is empty; cancels the upstream |
| `flattenIterable()` | Emits every element of every upstream iterable, in order |
| `flowFromSuspend(block)` | Cold flow that emits the single result of a suspending call |
| `getAndSet(value)` | Atomically replaces a `MutableStateFlow` value and returns the previous one |
| `increment(by)` | Atomically adds to a `MutableStateFlow<Int>` or `<Long>` and returns the new value |
| `intersperse(separator)` | Emits a separator between consecutive values |
| `intervalFlow(period, initialDelay)` | Infinite flow of sequential numbers on a fixed period |
| `lastOrDefault(default)` | Last value, or the default when the flow is empty; collects to completion |
| `mapAsync(concurrency, transform)` | Concurrent mapping that preserves upstream order |
| `mapAsyncUnordered(concurrency, transform)` | Concurrent mapping that emits in completion order |
| `mapIndexed(transform)` | Maps with access to each value's 0-based position |
| `mapState(transform)` | Coroutine-free derived `StateFlow` view |
| `mapToUnit()` | Replaces every value with `Unit` |
| `neverFlow()` | Never emits, never completes |
| `onEachIndexed(action)` | Runs an action with each value and its position, passes values through |
| `onFirst(action)` | Runs an action with the first value only, passes all values through |
| `pairwise()` / `pairwise(transform)` | Emits consecutive pairs, or a transform of each pair |
| `retryWithBackoff(retries, initialDelay, factor, maxDelay, retryIf)` | Re-collects a failed upstream with exponential delays |
| `runningCount()` | Emits the running number of values collected so far, starting at 1 |
| `startWith(value)` / `startWith(vararg)` | Prefixes values before the upstream |
| `stateFlowOf(value)` | `StateFlow` that always holds one value |
| `takeFor(duration)` | Mirrors the upstream for a duration, then completes |
| `takeUntil(signal)` | Mirrors the upstream until a signal flow emits, then completes |
| `takeWhileInclusive(predicate)` | Like `takeWhile`, but also emits the value that fails the predicate |
| `throttleFirst(window)` | Emits the first value per window, drops the rest |
| `throttleLatest(window)` | Emits the newest value once per window |
| `timeoutBetweenEmissions(timeout)` | Fails when the gap between emissions exceeds the timeout |
| `timerFlow(delay)` | Emits one `Unit` after a delay, then completes |
| `toggle()` | Atomically flips a `MutableStateFlow<Boolean>` and returns the new value |
| `windowed(size, step)` | Emits sliding windows of full lists |
| `withLatestFrom(other[, transform])` | Combines each upstream value with the latest value of another flow |
| `withPrevious()` | Pairs each value with its predecessor, `null` for the first |
