# Flow utilities

Operators and StateFlow helpers from the `io.github.yuroyami.kitecore.flow` package. Everything on this page is common code: it runs on every KiteCore target and depends only on kotlinx.coroutines. Each snippet is copy-pasteable and compiles against the real API.

```kotlin
import io.github.yuroyami.kitecore.flow.*
import kotlin.time.Duration.Companion.milliseconds
```

## Ignore rapid repeats with throttleFirst

```kotlin
buttonClicks
    .throttleFirst(500.milliseconds)
    .collect { submitOrder() }
// The first click emits immediately. Clicks arriving within 500 ms of an
// emitted click are dropped. The window reopens 500 ms after each emission.
```

Dropped values are lost, not conflated: after the window reopens, the next value to arrive is emitted, not the last one that was dropped. The window starts when the collector has finished with the emitted value, so a slow collector makes the window longer. Cancelling the collector cancels the upstream and the internal timer.

## Concurrent mapping with mapAsync

```kotlin
import kotlinx.coroutines.flow.asFlow

userIds.asFlow()
    .mapAsync(concurrency = 4) { id -> api.fetchUser(id) }
    .collect { user -> println(user.name) }
// Up to 4 fetches run at once; results arrive in userIds order.
```

A result is emitted only after the results of all earlier values have been emitted, so one slow transform delays later results. The upstream is collected only while permits are available, which bounds memory at `concurrency` pending results. A failure in the transform or the upstream cancels all in-flight work and fails the flow.

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

## Reference

Every public declaration in `io.github.yuroyami.kitecore.flow`, alphabetically. Overloads share a row.

| Declaration | Summary |
|---|---|
| `combineStates(a, b[, c[, d]], transform)` | Coroutine-free combined `StateFlow` view of 2 to 4 sources |
| `mapAsync(concurrency, transform)` | Concurrent mapping that preserves upstream order |
| `mapState(transform)` | Coroutine-free derived `StateFlow` view |
| `throttleFirst(window)` | Emits the first value per window, drops the rest |
