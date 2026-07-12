# Progress-reporting futures

`ProgressFuture<T>` is a `Deferred<T>` that also exposes live progress as a `StateFlow`. Start one with `asyncWithProgress`, observe `progress`, and `await()` the result as usual. The intended granularity is one future per user-visible operation, such as opening a document or downloading a file, not one per inner step.

## Start a job that reports progress

`asyncWithProgress` launches a coroutine like `async` (its `context` and `start` parameters behave the same) and runs the block with a `ProgressScope` receiver:

```kotlin
import io.github.yuroyami.kitecore.ProgressFuture
import io.github.yuroyami.kitecore.asyncWithProgress

val opening: ProgressFuture<Document> = scope.asyncWithProgress {
    report(null, "connecting")   // indeterminate, labeled
    val bytes = fetchBytes()
    report(0.4f)                 // 40%, keeps the "connecting" label
    report(0.4f, "parsing")      // same fraction, new label
    parse(bytes)
}

val doc = opening.await()
```

`report(fraction, phase)` takes a fraction in `0..1`, or `null` for indeterminate. It never throws: an out-of-range fraction is clamped, and `NaN` becomes indeterminate. It may be called from any thread. Prefer coarse units such as per page or per percent over per byte, since each distinct report allocates and wakes collectors.

Phase labels are sticky. A non-null `phase` updates the scope's label, and later reports without one keep it, so a stage's label does not need re-passing on every fraction tick. Update phases from the job's own flow, not from concurrent children.

## Divide the bar into slices

`slice(weight, phase)` allocates the next portion of the current window to a sub-scope. The child maps its own `0..1` reports into that window, and its slices nest further:

```kotlin
val opening = scope.asyncWithProgress {
    val header = slice(0.05f, "header")
    val xref   = slice(0.25f, "xref")
    val pages  = slice(0.70f, "pages")

    header.report(1f)                 // overall bar reaches 5%
    val table = parseXref(xref)       // pass the sub-scope down
    loadPages(table) { done, total ->
        pages.report(done, total)     // fills the remaining 70%
    }
}
```

Slice rules:

- Call `slice` sequentially from the job's own flow. Slices are windows allocated left to right, not a concurrent tree.
- Weights are fractions of the current window and should sum to at most 1. A weight beyond the remaining window is clamped.
- A named slice reports `0f` immediately, so the bar shows the new phase at its start. An unnamed slice inherits the current label.

## Count parallel work

For fan-out work, do not slice per task. Route completions through the `report(done, total)` overload instead:

```kotlin
import io.github.yuroyami.kitecore.ioDispatcher

val downloads = scope.asyncWithProgress {
    val urls = pendingUrls()
    val total = urls.size.toLong()
    report(0, total, "downloading")
    val done = MutableStateFlow(0L)
    urls.map { url ->
        async(ioDispatcher) {
            download(url)
            report(done.updateAndGet { it + 1 }, total)
        }
    }.awaitAll()
}
```

`report(done, total)` reports `done / total`, or indeterminate when `total <= 0` (useful while the item count is still unknown).

## Collect the progress

`progress` is a conflated `StateFlow<Progress>`: it always holds the latest value, and a slow collector skips intermediate ones. Collect it anywhere a flow works:

```kotlin
opening.progress.collect { p ->
    statusBar.fraction = p.fraction   // null while indeterminate
    statusBar.label = p.phase
}
```

In Compose, observe it with `collectAsState()`:

```kotlin
@Composable
fun OpeningIndicator(opening: ProgressFuture<Document>) {
    val p by opening.progress.collectAsState()
    val fraction = p.fraction
    if (fraction == null) LinearProgressIndicator()
    else LinearProgressIndicator(progress = { fraction })
    Text(p.phase.orEmpty())
}
```

Apply progress policy with ordinary `Flow` operators, for example `progress.sample(100.milliseconds)` to throttle UI updates.

## Success, failure, and cancellation

- On success, progress completes to `fraction = 1f` automatically, keeping the last phase. A final `report(1f)` is not needed.
- On failure or cancellation, progress freezes at the last reported value. Read the outcome from the `Deferred` side (`await()` rethrows, `isCancelled`), not from the bar.
- Every `Deferred` member works: `await()`, `cancel()`, `awaitAll(...)`, and `onAwait` in `select`.

## Adapt an existing Deferred

`withProgress` glues a `Deferred` and an external `StateFlow<Progress>` into a `ProgressFuture`, for APIs that already expose both halves. A Ktor download is the typical case:

```kotlin
import io.github.yuroyami.kitecore.Progress
import io.github.yuroyami.kitecore.withProgress

val state = MutableStateFlow(Progress(null, "downloading"))

val download = scope.async {
    client.get(url) {
        onDownload { sent, total ->
            state.value = Progress(
                fraction = if (total != null && total > 0) sent.toFloat() / total else null,
                phase = "downloading",
            )
        }
    }.body<ByteArray>()
}.withProgress(state)
```

The adapter passes your flow through untouched: unlike `asyncWithProgress`, it does not complete progress to `1f` on success. Emit the final value from your own callback if the consumer expects one.

## Progress value semantics

`Progress` is an immutable snapshot of a `fraction` and an optional `phase`, so a collector always renders a fraction and a label that belong together.

- The constructor never throws: an out-of-range fraction is clamped into `0..1`, and `NaN` becomes `null`.
- `fraction` is `null` while the total is unknown (indeterminate).
- Equality is by value over `(fraction, phase)`. `StateFlow` conflates on `equals`, so reporting an equal progress twice wakes no collectors.
- `toString()` renders as `Progress(42%)`, `Progress(42%, pages)`, or `Progress(indeterminate)`.
