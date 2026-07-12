# General utilities

Small building blocks for everyday Kotlin: conditional scope functions, error capture, `Result` composition, tuple helpers, resource management, and a weakly cached lazy. Each snippet is copy-pasteable and uses only real APIs from `io.github.yuroyami.kitecore.util`.

## Conditional scope functions

### Configure a builder conditionally

`applyIf` runs a block on the receiver when the condition holds and returns the receiver either way, so it slots into builder chains without breaking them. `applyUnless` is the negated form, and `alsoIf` is the conditional counterpart of `also`: the block receives the value as an argument, which fits side effects such as logging.

```kotlin
import io.github.yuroyami.kitecore.util.alsoIf
import io.github.yuroyami.kitecore.util.applyIf
import io.github.yuroyami.kitecore.util.applyUnless

fun buildCommand(verbose: Boolean, dryRun: Boolean, extra: List<String>): List<String> =
    mutableListOf("tool", "sync")
        .applyIf(verbose) { add("--verbose") }       // block only runs when verbose
        .applyUnless(dryRun) { add("--commit") }     // block only runs when NOT dryRun
        .apply { addAll(extra) }
        .alsoIf(verbose) { println("command: $it") } // side effect, receiver passes through
```

### Replace a value conditionally

`applyIf` mutates the receiver in place. When the type is immutable, use `transformIf`: the block produces a replacement value.

```kotlin
import io.github.yuroyami.kitecore.util.transformIf

val label = name.transformIf(name.length > 12) { it.take(12) + "..." }
// "Amadeus" is returned unchanged; longer names are truncated
```

## Error capture

### Fall back to null, a default, or a computed value

```kotlin
import io.github.yuroyami.kitecore.util.tryOrDefault
import io.github.yuroyami.kitecore.util.tryOrElse
import io.github.yuroyami.kitecore.util.tryOrNull

val port: Int? = tryOrNull { text.toInt() }
// null when text is not a valid Int

val timeout: Int = tryOrDefault(30) { settings.getValue("timeout").toInt() }
// 30 when the key is missing or the value does not parse

val level: Int = tryOrElse({ e -> println("bad level: $e"); 0 }) { text.toInt() }
// The fallback receives the exception, for logging or deriving a value
```

All three catch `Exception` only. `Error`s and other non-Exception throwables propagate to the caller.

### Suspend code: prefer runCatchingCancellable

`tryOrNull`, `tryOrDefault`, and `tryOrElse` are not suspend-aware. `CancellationException` is an `Exception`, so wrapping a suspending call in them swallows cancellation and the coroutine keeps running after its scope asked it to stop. In suspend code, use `runCatchingCancellable` from `io.github.yuroyami.kitecore.coroutines`: ordinary failures become `Result.failure`, while cancellation is rethrown and never captured.

```kotlin
import io.github.yuroyami.kitecore.coroutines.runCatchingCancellable

suspend fun loadGreeting(): String? =
    runCatchingCancellable { fetchGreetingFromServer() }.getOrNull()
// A network failure yields null; cancelling the caller still cancels normally
```

A receiver form also exists, mirroring the receiver form of `runCatching`:

```kotlin
suspend fun HttpClient.greeting(): Result<String> =
    runCatchingCancellable { get("/greeting") }
```

## Result composition

### Chain operations that each return a Result

`flatMap` is the counterpart of `Result.map` for transforms that themselves produce a `Result`, avoiding the nested `Result<Result<R>>`. Exceptions thrown by the transform are not caught, matching `Result.map` rather than `Result.mapCatching`.

```kotlin
import io.github.yuroyami.kitecore.util.flatMap

fun readConfig(): Result<String> = runCatching { loadConfigText() }
fun parsePort(raw: String): Result<Int> = runCatching { raw.trim().toInt() }

val port: Result<Int> = readConfig().flatMap { parsePort(it) }
// Result<Int>, not Result<Result<Int>>; the first failure short-circuits
```

`flatten` collapses one level of nesting after the fact. An outer failure stays a failure; an outer success unwraps to the inner `Result`.

```kotlin
import io.github.yuroyami.kitecore.util.flatten

val nested: Result<Result<Int>> = runCatching { parsePort("8080") }
val flat: Result<Int> = nested.flatten()
```

### Translate failures into domain exceptions

`mapFailure` is the failure-side counterpart of `Result.map`. Successes pass through unchanged.

```kotlin
import io.github.yuroyami.kitecore.util.mapFailure

val config = readConfig()
    .mapFailure { IllegalStateException("Configuration could not be read", it) }
// A low-level failure is rewrapped; the original exception rides along as the cause
```

### Combine independent Results

`zip` combines two or three `Result`s into one. The combine function runs only when every input is a success; otherwise the earliest failure in declaration order is returned and later exceptions are dropped.

```kotlin
import io.github.yuroyami.kitecore.util.zip

val dimensions: Result<Pair<Int, Int>> =
    parsePort(widthText).zip(parsePort(heightText)) { w, h -> w to h }
// Failure when either input fails; the first failure wins
```

### A validation pipeline

The three-way `zip` plus the query helpers make a compact validation pipeline. `isSuccessAnd` returns `true` only for a success whose value satisfies the predicate; `isFailureAnd` returns `true` only for a failure whose exception satisfies it. Both mirror Rust's `Result::is_ok_and` and `Result::is_err_and`, and neither invokes the predicate on the other branch.

```kotlin
import io.github.yuroyami.kitecore.util.flatMap
import io.github.yuroyami.kitecore.util.isFailureAnd
import io.github.yuroyami.kitecore.util.isSuccessAnd
import io.github.yuroyami.kitecore.util.zip

data class Signup(val name: String, val email: String, val age: Int)

fun validateName(raw: String): Result<String> =
    if (raw.isNotBlank()) Result.success(raw.trim())
    else Result.failure(IllegalArgumentException("name is blank"))

fun validateEmail(raw: String): Result<String> =
    if ("@" in raw) Result.success(raw)
    else Result.failure(IllegalArgumentException("email has no @"))

fun validateAge(raw: String): Result<Int> =
    runCatching { raw.toInt() }.flatMap {
        if (it >= 18) Result.success(it)
        else Result.failure(IllegalArgumentException("must be 18 or older"))
    }

val signup: Result<Signup> =
    validateName(nameInput).zip(validateEmail(emailInput), validateAge(ageInput)) {
        name, email, age -> Signup(name, email, age)
    }
// Success only when all three pass; otherwise the earliest failure

if (signup.isSuccessAnd { it.age < 21 }) {
    // Valid signup that also needs an extra step, no unwrapping required
}

if (signup.isFailureAnd { it is IllegalArgumentException }) {
    // Validation failure specifically, as opposed to some other exception type
}
```

## Tuples

### Pair helpers

```kotlin
import io.github.yuroyami.kitecore.util.mapBoth
import io.github.yuroyami.kitecore.util.mapFirst
import io.github.yuroyami.kitecore.util.mapSecond
import io.github.yuroyami.kitecore.util.swapped
import io.github.yuroyami.kitecore.util.toTriple

val entry = "port" to "8080"

entry.swapped()                                    // ("8080", "port")
entry.mapFirst { it.uppercase() }                  // ("PORT", "8080")
entry.mapSecond { it.toInt() }                     // ("port", 8080)
entry.mapBoth({ it.uppercase() }, { it.toInt() })  // ("PORT", 8080)
entry.toTriple(true)                               // ("port", "8080", true)
```

`mapFirst` and `mapSecond` carry the other component over unchanged. `mapBoth` transforms both components in a single call.

### Triple helpers

`mapFirst`, `mapSecond`, and `mapThird` exist for `Triple` as well, again leaving the other components untouched. The `drop` functions reduce a `Triple` to a `Pair` by discarding one component.

```kotlin
import io.github.yuroyami.kitecore.util.dropFirst
import io.github.yuroyami.kitecore.util.dropSecond
import io.github.yuroyami.kitecore.util.dropThird
import io.github.yuroyami.kitecore.util.mapFirst
import io.github.yuroyami.kitecore.util.mapSecond
import io.github.yuroyami.kitecore.util.mapThird

val point = Triple(3, 4, 5)

point.mapFirst { it * 10 }   // (30, 4, 5)
point.mapSecond { it * 10 }  // (3, 40, 5)
point.mapThird { it * 10 }   // (3, 4, 50)
point.dropFirst()            // (4, 5)
point.dropSecond()           // (3, 5)
point.dropThird()            // (3, 4)
```

## Booleans

`orFalse` and `orTrue` resolve a nullable `Boolean` and read better than `?: false` or `?: true` inside longer expressions. Pick the one that matches the meaning of absence: `orFalse` when absent means denied, `orTrue` when absent means enabled.

```kotlin
import io.github.yuroyami.kitecore.util.orFalse
import io.github.yuroyami.kitecore.util.orTrue
import io.github.yuroyami.kitecore.util.toInt

val isAdmin: Boolean? = null
isAdmin.orFalse()  // false: a missing permission is a denied permission
isAdmin.orTrue()   // true: a missing setting keeps the feature on

val checks = listOf(true, false, true)
val passed = checks.sumOf { it.toInt() }  // 2: true counts as 1, false as 0
```

## Resources

### Close several resources with one block

`useAll` is the multi-resource counterpart of `kotlin.use`. It runs the block, then closes every resource in reverse order: the resource listed last, typically the one layered on top of the others, is closed first. Every resource is closed exactly once, no matter how many closes fail.

```kotlin
import io.github.yuroyami.kitecore.util.useAll

val result = useAll(source, buffer, cipher) {
    cipher.process(buffer.read(source))
}
// Close order: cipher, then buffer, then source
```

An `Iterable<AutoCloseable>` form behaves identically, closing in reverse iteration order:

```kotlin
val shards: List<AutoCloseable> = openShards()

shards.useAll {
    // Work with every shard open
}
// The last shard in the list is closed first
```

Failure handling uses `Throwable.addSuppressed`, and no failure is ever silently dropped:

- When the block throws, that exception is rethrown, and every close failure is attached to it as a suppressed exception.
- When the block succeeds but closing fails, the first close failure in the reverse close order is thrown after all remaining resources have been closed, with any later close failures attached to it as suppressed exceptions.

### Close without masking an earlier failure

`closeQuietly` swallows any `Exception` thrown by `close()`. Use it on cleanup paths where a secondary close failure must not mask the failure already being handled. `Error`s and other non-Exception throwables still propagate.

```kotlin
import io.github.yuroyami.kitecore.util.closeQuietly

try {
    work(socket)
} finally {
    socket.closeQuietly()
    // If close() throws an Exception here, the failure from work() is preserved
}
```

## WeakLazy

### Cache an expensive but reconstructible value

`weakLazy` returns a `WeakLazy<T>`: a lazily computed value cached through a weak reference, so the cached instance can be garbage collected under memory pressure and recomputed on the next read. It fits objects that are expensive to build but cheap to rebuild from their inputs, such as parsed resources, lookup tables, or renderers.

```kotlin
import io.github.yuroyami.kitecore.util.weakLazy

val table = weakLazy { loadHyphenationTable() }

val t = table.value   // First read runs the initializer and caches the result weakly
t.hyphenate(word)     // Hold the value in a local while using it
```

Reading `value` returns the cached instance while it is alive; once it has been collected, the initializer runs again and a fresh instance is cached. The initializer can therefore run more than once and must tolerate that. Keep the returned value in a local for the duration of its use, because two reads of `value` may compute twice once the first instance becomes unreachable.

### Inspect, refresh, and drop the cache

```kotlin
table.peek()     // The cached instance, or null; never runs the initializer
table.refresh()  // Drops the cache, recomputes eagerly, returns the fresh value
table.clear()    // Drops the cache; the next read of value recomputes
```

`peek()` returning `null` means the value was never computed, was cleared, or has been collected. Use `refresh()` when the underlying data changed and the next reader must not observe a stale instance.

### Targets without weak references

On targets where `WeakRef.isWeakSupported` is `false` (Kotlin/Wasm, and JS runtimes without ES2021 `WeakRef`) the cached instance is held strongly and is never collected. After the first read, `WeakLazy` then behaves like a plain `lazy`, although `refresh()` and `clear()` still replace or drop the cache. On those targets, `clear()` is the only way the cached instance is released.

```kotlin
import io.github.yuroyami.kitecore.WeakRef

if (!WeakRef.isWeakSupported) {
    table.clear()  // Release the strongly held cache by hand
}
```

`WeakLazy` is not thread-safe. Concurrent reads may each run the initializer and observe different instances; confine an instance to one thread or add external synchronization.
