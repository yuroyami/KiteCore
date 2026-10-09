# Module kitecore

Runtime utilities for Kotlin Multiplatform, for the pieces that `commonMain` does not have.

KiteCore has four platform primitives (`ioDispatcher`, `asyncWithProgress`, `WeakRef` and
`Platform`), a portable `String.format`, and a short list of coroutine and Flow helpers.
Its only dependency is `kotlinx-coroutines-core`. It runs on Android, the JVM, iOS, macOS,
JS and Wasm.

| You want to | Use |
| --- | --- |
| Run blocking calls in common code | `ioDispatcher` |
| Show the progress of a long operation | `asyncWithProgress` |
| Point to an object without keeping it alive | `WeakRef` |
| Know the OS and device | `Platform.current` |
| Format text with Java's syntax | `String.format` |
| Retry, run in parallel, share work | the `coroutines` package |
| Throttle a flow, derive a `StateFlow` | the `flow` package |

# Package io.github.yuroyami.kitecore

The platform primitives: each one gives common code something that only some targets have.

Start here:

- `ioDispatcher`: the dispatcher for blocking calls, on every target.
- `asyncWithProgress`: an `async` that also publishes its progress.
- `WeakRef`: a weak reference from common code.
- `Platform.current`: the OS name, version and device model.

# Package io.github.yuroyami.kitecore.text

A `String.format` for common code, with the syntax and output of `java.util.Formatter`.

Start here: [format]. Import `io.github.yuroyami.kitecore.text.format` to use it.

# Package io.github.yuroyami.kitecore.coroutines

Suspend helpers that handle cancellation correctly.

Start here:

- [retryWithBackoff]: run a call again after a failure, with a growing pause.
- [runCatchingCancellable]: `runCatching` that never swallows the cancellation of the caller.
- [parallelMap]: map a collection with many calls at the same time.
- [SingleFlight]: callers that ask for the same key at the same time share one call.
- [suspendLazy]: a lazy value with a suspending initializer.

# Package io.github.yuroyami.kitecore.flow

Flow operators and `StateFlow` helpers.

Start here:

- [throttleFirst]: let one value through, then ignore values for a while.
- [mapAsync]: map a flow with many calls at the same time, in order.
- [mapState]: derive a `StateFlow` from a `StateFlow`, without a `CoroutineScope`.
- [combineStates]: combine two to four `StateFlow`s into one.
