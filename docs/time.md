# Time utilities

Walkthroughs for everything in `io.github.yuroyami.kitecore.time`. Each snippet
is copy-pasteable and uses only real APIs from KiteCore 0.1.0; expected
behavior is shown in comments. Everything builds on `kotlin.time.Duration`.

`Stopwatch`, `Deadline`, `RateLimiter`, and `withMinimumDuration` all accept a
`TimeSource.WithComparableMarks`, defaulting to `TimeSource.Monotonic`. Pass a
`kotlin.time.TestTimeSource` in tests to control the clock; examples below show
the pattern.

## Stopwatch: measure phases of work

`Stopwatch` accumulates elapsed time across start and stop cycles, which makes
it the right tool when one measurement spans several phases with pauses in
between, such as timing the active portions of a sync job.

```kotlin
import io.github.yuroyami.kitecore.time.Stopwatch

val watch = Stopwatch()   // starts out stopped, elapsed == Duration.ZERO

watch.start()             // a second start while running does nothing
downloadChanges()
watch.stop()              // folds the running segment into elapsed;
                          // a stop while stopped does nothing

// ... user interaction we do not want to measure ...

watch.start()             // resumes on top of the accumulated time
applyChanges()
watch.stop()

println(watch.elapsed)    // download time + apply time
println(watch.isRunning)  // false
```

`elapsed` updates live while the stopwatch runs, so it can drive a ticking
label without stopping the watch.

`measure` runs a block with the stopwatch running and stops it afterwards,
even when the block throws, returning the block's result:

```kotlin
val report = watch.measure { buildReport() }
// the time buildReport took is now part of watch.elapsed
```

`reset` versus `restart`:

```kotlin
watch.reset()     // stops the watch and clears elapsed to Duration.ZERO
watch.restart()   // clears elapsed and starts again, one call instead of
                  // reset() followed by start()
```

Testing with an injected time source:

```kotlin
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds

val time = TestTimeSource()
val watch = Stopwatch(timeSource = time)

watch.start()
time += 3.seconds
watch.elapsed   // exactly 3s, no real waiting
```

`Stopwatch` is not thread-safe. Confine each instance to a single thread or
coroutine, or guard it with external synchronization.

## Deadline: give an operation a time budget

`Deadline` is a fixed time budget that starts counting down at construction.
Use it when several steps share one budget: whatever the first step consumes
is no longer available to the second.

```kotlin
import io.github.yuroyami.kitecore.time.Deadline
import kotlin.time.Duration.Companion.seconds

suspend fun loadDashboard(): Dashboard {
    val deadline = Deadline(timeout = 2.seconds)   // the countdown starts here

    val profile = deadline.orNull { api.fetchProfile() }   // bounded by remaining
    val feed = deadline.orNull { api.fetchFeed() }         // bounded by what is left

    return Dashboard(profile, feed)   // null fields render as placeholders
}
```

`orNull` runs the block under `withTimeoutOrNull(remaining)` and returns null
on expiry. A deadline that has already expired returns null without running
the block at all, so a chain of `orNull` calls degrades cleanly: once the
budget is gone, the remaining steps are skipped.

The properties:

```kotlin
deadline.timeout      // 2s, the full budget the deadline was created with
deadline.elapsed      // time since construction, grows without bound past timeout
deadline.remaining    // time left, floored at Duration.ZERO
deadline.isExpired    // true once elapsed >= timeout
deadline.progress     // expired fraction of timeout, 0.0 to 1.0; feeds progress bars
```

A non-positive `timeout` produces a deadline that is expired from the start,
with `progress` at 1.0.

`checkNotExpired` turns a quiet expiry into a failure, useful at the top of a
loop iteration that must not start work it cannot finish:

```kotlin
deadline.checkNotExpired()   // throws IllegalStateException when isExpired
```

`await` suspends for the remaining time in a single delay. The wait length is
read once when the call starts, and an expired deadline returns immediately:

```kotlin
deadline.await()   // wakes up at (roughly) the moment the deadline expires
```

All state is captured at construction, so reading the properties is safe from
any coroutine. The constructor accepts a time source for tests, like
`Stopwatch`.

## RateLimiter: throttle client-side API calls

`RateLimiter` is a sliding-window limiter: it grants at most `permits`
acquisitions within any window of length `per`. It remembers the time mark of
each recent acquisition and grants a permit whenever fewer than `permits`
marks are younger than `per`. Use it to stay under a server's documented rate
limit instead of discovering it through 429 responses.

```kotlin
import io.github.yuroyami.kitecore.time.RateLimiter
import kotlin.time.Duration.Companion.seconds

// The API allows 5 requests per second. Both values must be positive.
val limiter = RateLimiter(permits = 5, per = 1.seconds)

suspend fun search(query: String): Results {
    limiter.acquire()          // suspends until a permit is free, then consumes it
    return api.search(query)
}
```

`acquire` versus `tryAcquire`: `acquire` waits, `tryAcquire` reports. Use
`tryAcquire` for optional work that should be skipped rather than queued:

```kotlin
if (limiter.tryAcquire()) {
    prefetchNextPage()   // permit claimed without suspending
}
// else: no permit free right now, or the limiter is busy; skip the prefetch
```

`tryAcquire` also returns false while another coroutine is inside `acquire` or
`tryAcquire`, so a false result does not always mean the window is full.

`withPermit` combines acquisition with a block:

```kotlin
val results = limiter.withPermit { api.search(query) }
```

The permit is consumed rather than held: it frees on its own once its mark
leaves the sliding window, regardless of how long the block runs.

Inspection and configuration are exposed as properties:

```kotlin
limiter.permits            // 5
limiter.per                // 1s
limiter.availablePermits   // best-effort snapshot; can be stale as soon as it
                           // is read, so claim permits with tryAcquire, not this
```

Thread-safety, as stated in the KDoc: `acquire` and `tryAcquire` are
coroutine-safe. Shared state is guarded by a `Mutex`, and a coroutine
suspended in `acquire` holds the mutex while it waits, so permits are granted
to waiters in arrival order. The constructor accepts a time source for tests.

## Duration formatting and parsing

Extension functions on `Duration` for the common display shapes. All three
formatters render negative durations with a leading minus sign and infinite
durations as `"Infinity"`.

`formatHms` writes hours, minutes, and seconds, truncated to whole seconds.
Leading zero units are omitted, inner zero units are kept:

```kotlin
import io.github.yuroyami.kitecore.time.formatHms
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

5025.seconds.formatHms()    // "1h 23m 45s"
65.seconds.formatHms()      // "1m 5s"
3605.seconds.formatHms()    // "1h 0m 5s"   inner zero kept
Duration.ZERO.formatHms()   // "0s"
(-90).seconds.formatHms()   // "-1m 30s"
```

`formatClock` writes a colon-separated clock string, truncated to whole
seconds. Under an hour the shape is `MM:SS`; from an hour up it is `HH:MM:SS`,
and the hour field grows past two digits when needed:

```kotlin
83.seconds.formatClock()       // "01:23"
5025.seconds.formatClock()     // "01:23:45"
360000.seconds.formatClock()   // "100:00:00"
```

`humanReadable` writes the most significant components, from days down to
milliseconds. Zero components are skipped and at most `maxParts` components
are emitted; `maxParts` defaults to 2 and must be at least 1:

```kotlin
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

(26.hours + 3.minutes + 4.seconds).humanReadable()    // "1d 2h"
(26.hours + 3.minutes).humanReadable(maxParts = 3)    // "1d 2h 3m"
1500.milliseconds.humanReadable()                     // "1s 500ms"
Duration.ZERO.humanReadable()                         // "0s"
```

`parseClock` and `parseClockOrNull` read the `formatClock` shapes back. Two
colon-separated fields parse as minutes and seconds, three as hours, minutes,
and seconds. The leading field may have any number of digits; every following
field must have exactly two digits and be at most 59. A single leading minus
sign negates the result.

```kotlin
import io.github.yuroyami.kitecore.time.parseClock
import io.github.yuroyami.kitecore.time.parseClockOrNull

parseClock("01:23:45")      // 1h 23m 45s
parseClock("23:45")         // 23m 45s
parseClock("123:00:00")     // 123 hours: the leading field is unrestricted
parseClock("-05:00")        // -(5 minutes)

parseClockOrNull("1:2:3")   // null: trailing fields need exactly two digits
parseClockOrNull("00:75")   // null: seconds above 59
parseClock("00:75")         // throws IllegalArgumentException
```

The rounding trio snaps a duration to the nearest whole second, minute, or
hour, with ties rounding away from zero. `roundToSeconds` rounds at
millisecond precision; `roundToMinutes` and `roundToHours` round at second
precision. Infinite durations are returned unchanged.

```kotlin
1499.milliseconds.roundToSeconds()    // 1s
1500.milliseconds.roundToSeconds()    // 2s: tie rounds away from zero
(-1500).milliseconds.roundToSeconds() // -2s
89.seconds.roundToMinutes()           // 1m
90.seconds.roundToMinutes()           // 2m
30.minutes.roundToHours()             // 1h
```

`sum` and `average` aggregate any `Iterable<Duration>`:

```kotlin
import io.github.yuroyami.kitecore.time.average
import io.github.yuroyami.kitecore.time.sum

val laps = listOf(61.seconds, 65.seconds, 66.seconds)
laps.sum()                        // 3m 12s
laps.average()                    // 1m 4s

emptyList<Duration>().sum()       // Duration.ZERO
emptyList<Duration>().average()   // throws NoSuchElementException
```

## Suspend helpers

`withMinimumDuration` runs a block and then delays so the whole call takes at
least the given minimum. This keeps a loading indicator from flickering when
the operation finishes in a few milliseconds:

```kotlin
import io.github.yuroyami.kitecore.time.withMinimumDuration
import kotlin.time.Duration.Companion.milliseconds

suspend fun refresh() {
    showSpinner()
    val data = withMinimumDuration(500.milliseconds) {
        api.fetch()   // suppose this returns after 80 ms
    }
    // the call took at least 500 ms total, so the spinner was visible
    // long enough to read rather than flashing
    hideSpinner()
    render(data)
}
```

When the block takes the minimum or longer, no extra delay is added. When the
block throws, the exception propagates without any padding delay. The function
accepts a `timeSource` parameter, `TimeSource.Monotonic` by default, so tests
can control the measurement.

`pollUntil` checks a suspending condition until it returns true or a timeout
elapses, and reports which happened:

```kotlin
import io.github.yuroyami.kitecore.time.pollUntil
import kotlin.time.Duration.Companion.seconds

val ready = pollUntil(interval = 200.milliseconds, timeout = 10.seconds) {
    server.isHealthy()   // suspend () -> Boolean
}
// true: the condition became true within the timeout
// false: the timeout elapsed first
```

The condition is checked once immediately, then again after every `interval`.
A non-positive `timeout` still performs the immediate check. `interval` must
be positive or the call throws `IllegalArgumentException`.

`backoffSequence` produces an infinite sequence of exponentially growing retry
delays. The first element is `initialDelay`, every following element is the
previous one multiplied by `factor` (2.0 by default), and every element is
capped at `maxDelay` (`Duration.INFINITE` by default). The sequence can be
iterated any number of times.

```kotlin
import io.github.yuroyami.kitecore.time.backoffSequence
import kotlinx.coroutines.delay

val delays = backoffSequence(
    initialDelay = 250.milliseconds,
    factor = 2.0,
    maxDelay = 8.seconds,
)
// yields 250ms, 500ms, 1s, 2s, 4s, 8s, 8s, 8s, ...

suspend fun connectWithRetry(): Connection {
    for (wait in delays.take(6)) {
        connectOrNull()?.let { return it }
        delay(wait)
    }
    error("could not connect after 6 attempts")
}
```

`initialDelay` and `maxDelay` must not be negative, and `factor` must be at
least 1.0; violations throw `IllegalArgumentException`.
