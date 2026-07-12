# Data structures

Walkthroughs for every class in `io.github.yuroyami.kitecore.structures`. Each
snippet is copy-pasteable and uses only real APIs from KiteCore 0.1.0; expected
behavior is shown in comments.

None of the classes in this package are thread-safe. Confine each instance to a
single thread or coroutine, or guard it with external synchronization.

## LruCache: a bounded cache for decoded images

`LruCache` holds at most `maxSize` entries and evicts the least recently used
entry when a new key arrives at capacity. Decoded images are a natural fit:
decoding is expensive, memory is finite, and the images the user saw most
recently are the ones most likely to be needed again.

```kotlin
import io.github.yuroyami.kitecore.structures.LruCache

// Hold at most 3 decoded images. maxSize must be positive
// or the constructor throws IllegalArgumentException.
val images = LruCache<String, Bitmap>(maxSize = 3)

images.put("avatar", decode("avatar.png"))   // returns null: key was absent
images.put("banner", decode("banner.png"))
images.put("icon", decode("icon.png"))

images.get("avatar")   // hit: returns the bitmap, marks "avatar" most recently used
images.get("missing")  // null
```

Eviction happens on `put` when a new key would push the cache past `maxSize`.
The evicted value is discarded, not returned. Replacing an existing key never
evicts.

```kotlin
// The cache is full and "banner" is now the least recently used entry
// ("avatar" was refreshed by the get above).
images.put("logo", decode("logo.png"))   // evicts "banner"

images.containsKey("banner")   // false
images.containsKey("avatar")   // true, and containsKey does not affect recency
images.size                    // 3, never above images.maxSize
```

`getOrPut` is the decode-on-demand form: a hit marks the entry most recently
used, a miss computes the value, inserts it through `put`, and may evict.

```kotlin
val header = images.getOrPut("header") { decode("header.png") }
```

Inspection and removal. `keys()` and `toMap()` return snapshots in eviction
order, least recently used first, and like `containsKey` they do not affect
recency.

```kotlin
images.keys()            // ["avatar", "icon", ...] as a Set, LRU first
images.toMap()           // same order, with values
images.remove("icon")    // returns the removed bitmap, or null if absent
images.clear()           // images.size == 0
```

All operations run in amortized constant time.

## TtlCache: expiring API responses

`TtlCache` gives every entry a time-to-live measured from the moment it was
stored. Cached API responses are the typical use: serve the cached profile for
five minutes, then fetch a fresh one.

```kotlin
import io.github.yuroyami.kitecore.structures.TtlCache
import kotlin.time.Duration.Companion.minutes

val responses = TtlCache<String, UserProfile>(
    ttl = 5.minutes,   // must not be negative
    maxSize = 200,     // optional, defaults to Int.MAX_VALUE, must be positive
)

responses.put("user/42", profile)      // stored with a fresh age
responses.get("user/42")               // live: returns it, refreshes recency
```

An entry is live while its age is strictly less than `ttl`; at exactly `ttl` it
is expired. Expired entries are removed lazily: `get`, `getOrPut`, and `remove`
treat them as absent and delete them on contact.

```kotlin
// Six minutes later:
responses.get("user/42")     // null, and the expired entry is removed

val fresh = responses.getOrPut("user/42") { api.fetchProfile(42) }
// expired or absent counts as a miss: the block runs and the result is stored

responses.remove("user/42")  // returns the value only while it is live;
                             // an expired entry is removed but null is returned
```

Replacing a key with `put` restarts its time-to-live and refreshes recency.
`get` and a `getOrPut` hit refresh recency only, not the age.

Because removal is lazy, `size` includes expired entries that nothing has
touched yet. `purgeExpired` sweeps them eagerly in time linear in `size` and
returns how many it removed; `clear` drops everything, live or expired.

```kotlin
responses.size           // may include expired entries
responses.purgeExpired() // returns the number of expired entries removed
responses.clear()        // responses.size == 0
```

When `put` inserts a new key at `maxSize`, it first purges expired entries and
only then, if still full, evicts the least recently used live entry:

```kotlin
val small = TtlCache<String, String>(ttl = 1.minutes, maxSize = 2)
small.put("a", "1")
small.put("b", "2")
small.put("c", "3")   // purges expired entries first; still full, so "a" is evicted
small.get("a")        // null
```

Time is measured with the `TimeSource.WithComparableMarks` passed to the
constructor, `TimeSource.Monotonic` by default. Inject a
`kotlin.time.TestTimeSource` to control expiry in tests:

```kotlin
import kotlin.time.TestTimeSource

val time = TestTimeSource()
val cache = TtlCache<String, String>(ttl = 5.minutes, timeSource = time)

cache.put("token", "abc")
time += 5.minutes
cache.get("token")   // null: at exactly ttl the entry is expired
```

The `ttl` and `maxSize` values are readable as properties on the cache.

## RingBuffer: the last N log lines

`RingBuffer` is a fixed-capacity buffer that overwrites its oldest element when
full. Keeping the most recent log lines for a crash report is the classic use:
memory stays bounded no matter how long the app runs.

```kotlin
import io.github.yuroyami.kitecore.structures.RingBuffer

// capacity must be positive or the constructor throws IllegalArgumentException.
val recentLogs = RingBuffer<String>(capacity = 100)

fun log(line: String) {
    recentLogs.add(line)   // once 100 lines are held, the oldest is dropped
}
```

A smaller buffer makes the overwrite visible. `addAll` feeds every element of
an iterable through `add`, so when the input is longer than `capacity`, only
the last `capacity` elements remain.

```kotlin
val buffer = RingBuffer<String>(capacity = 3)

buffer.addAll(listOf("boot", "load config", "connect"))
buffer.isFull()        // true: size == capacity
buffer.add("ready")    // drops "boot"

buffer.toList()        // ["load config", "connect", "ready"], oldest first
buffer.first()         // "load config"; throws NoSuchElementException when empty
buffer.last()          // "ready"; throws NoSuchElementException when empty
buffer.size            // 3, never above buffer.capacity
```

The `OrNull` variants and iteration:

```kotlin
for (line in buffer) {
    // oldest first; the iterator walks a snapshot, so mutating the
    // buffer during the loop does not affect it
    println(line)
}

buffer.clear()
buffer.isEmpty()       // true
buffer.firstOrNull()   // null instead of throwing
buffer.lastOrNull()    // null
```

`add` runs in constant amortized time. The buffer is backed by `ArrayDeque`.

## Counter: tag frequencies

`Counter` is a multiset: it tracks how many times each element has been added.
Counting tag occurrences across a set of articles shows all of its operations.

```kotlin
import io.github.yuroyami.kitecore.structures.Counter

val tags = Counter<String>()

tags.add("kotlin")             // count 1
tags.add("kotlin")             // count 2
tags.add("multiplatform", 3)   // count parameter must not be negative
tags += "compose"              // plusAssign is shorthand for add(element)

tags.count("kotlin")           // 2
tags.count("rust")             // 0: absent elements count as zero
tags.total                     // 6: the sum of all counts
```

`mostCommon` returns up to `n` elements with their counts, highest count first,
with ties keeping first-insertion order. It runs in time proportional to the
number of distinct elements times its logarithm.

```kotlin
tags.mostCommon(2)   // [("multiplatform", 3), ("kotlin", 2)]
tags.mostCommon()    // every element, sorted; n defaults to Int.MAX_VALUE
```

Counts are always positive: `remove` floors at zero and deletes the key when
its count reaches zero. Removing an absent element has no effect.

```kotlin
tags.remove("kotlin")          // count drops to 1
tags.remove("kotlin", 10)      // floors at zero: the key is deleted
tags.count("kotlin")           // 0
```

Snapshots and iteration follow first-insertion order:

```kotlin
tags.toMap()                    // Map<String, Int> in first-insertion order
for ((tag, count) in tags) {    // iterates a snapshot; mutating the counter
    println("$tag: $count")     // during the loop does not affect it
}

tags.clear()                    // tags.total == 0
```

## ObjectPool: reusable byte buffers

`ObjectPool` stores idle instances so hot paths can reuse them instead of
allocating. Read buffers are the standard example: allocate an 8 KiB array
once, reuse it for every read.

```kotlin
import io.github.yuroyami.kitecore.structures.ObjectPool

val buffers = ObjectPool(
    capacity = 4,                       // at most 4 idle buffers are stored;
                                        // must not be negative
    factory = { ByteArray(8 * 1024) },  // runs only when borrow finds the pool empty
    reset = { it.fill(0) },             // optional; zeroes a buffer before it is stored
)
```

No instance is created until the first `borrow` on an empty pool. `borrow`
returns the most recently recycled instance first; `recycle` runs the reset
callback and stores the instance for a later `borrow`.

```kotlin
val buffer = buffers.borrow()   // pool empty: the factory creates one
try {
    readInto(buffer)
} finally {
    buffers.recycle(buffer)     // reset runs, then the buffer is stored
}

buffers.pooledCount             // 1, always between 0 and buffers.capacity
```

`use` wraps that pattern: it borrows, runs the block, and recycles the
instance even when the block throws. Do not touch the instance after the block
returns.

```kotlin
val checksum = buffers.use { buf ->
    readInto(buf)
    crc32(buf)   // the result of the block is returned
}
```

Two bounds to know: recycling into a pool that already holds `capacity`
instances drops the instance without invoking reset, and the pool never
verifies that a recycled instance came from `borrow`. Recycling a foreign or
already-recycled instance is a caller bug the pool will not catch.

## TypedMap and TypedKey: heterogeneous request attributes

`TypedMap` stores values of unrelated types in one map without casts at the
call site. Each entry is keyed by a `TypedKey<T>` and read back as `T`.
Request attributes in a networking layer are a typical use: one bag carries a
user id, a trace id, and a retry count, each with its own type.

Keys compare by identity, so two keys with the same name are distinct entries.
Create each key once, usually in a shared object, and reuse the instance. The
`name` is a diagnostic label only; `toString()` returns `TypedKey(name)`.

```kotlin
import io.github.yuroyami.kitecore.structures.TypedKey
import io.github.yuroyami.kitecore.structures.TypedMap

object RequestKeys {
    val UserId = TypedKey<Long>("userId")
    val TraceId = TypedKey<String>("traceId")
    val Retries = TypedKey<Int>("retries")
}

val attributes = TypedMap()

attributes[RequestKeys.UserId] = 42L          // set: replaces any previous value
attributes[RequestKeys.TraceId] = "req-9f3a"

val userId: Long? = attributes[RequestKeys.UserId]   // 42, typed, no cast
attributes.contains(RequestKeys.Retries)             // false
attributes.size                                      // 2

attributes.remove(RequestKeys.TraceId)   // returns "req-9f3a", or null if absent
attributes.clear()                       // attributes.size == 0
```

```kotlin
// Identity keying in action:
val other = TypedKey<Long>("userId")   // same name, different key
attributes[RequestKeys.UserId] = 42L
attributes[other]                      // null: not the same key instance
println(other)                         // "TypedKey(userId)"
```

Operations run in expected constant time.

## OnceFlag: run a block at most once

`OnceFlag` guards one-time work between resets, such as showing a tutorial
overlay the first time a screen scrolls.

```kotlin
import io.github.yuroyami.kitecore.structures.OnceFlag

val tutorial = OnceFlag()

fun onListScrolled() {
    tutorial.runOnce { showTutorialOverlay() }
}

onListScrolled()    // runs the block; runOnce returns true
onListScrolled()    // does nothing; runOnce returns false
tutorial.hasRun     // true

tutorial.reset()    // clears the flag
onListScrolled()    // runs the block again
```

The flag is set before the block is invoked. A block that throws therefore
still consumes the flag: the exception propagates, and later calls do not run
until `reset`.

## SingletonHolder: the KMP singleton-with-argument pattern

Kotlin's `object` cannot take a constructor argument. `SingletonHolder` is the
multiplatform companion-object pattern for a singleton that needs one: the
companion object of the singleton class extends the holder and callers go
through `getInstance`.

```kotlin
import io.github.yuroyami.kitecore.structures.SingletonHolder

class Analytics private constructor(private val config: Config) {
    fun track(event: String) { /* ... */ }

    companion object : SingletonHolder<Analytics, Config>(::Analytics)
}

val analytics = Analytics.getInstance(Config(apiKey = "k1"))  // creates the instance
val same = Analytics.getInstance(Config(apiKey = "other"))    // ignores the argument,
                                                              // returns the same instance
```

The creator function runs on the first `getInstance` call and its reference is
released afterwards, so anything it captured becomes collectable. The class is
not thread-safe: make sure the first `getInstance` call completes before other
threads call it, or add external synchronization.

## memoize: cache the results of pure functions

`memoize` wraps a pure function so each distinct argument is computed once.
Two overloads exist: one for a single argument, one for an argument pair.

```kotlin
import io.github.yuroyami.kitecore.structures.memoize

var calls = 0
val slugify = memoize { title: String ->
    calls++
    title.lowercase().replace(' ', '-')
}

slugify("Hello World")   // computes: "hello-world", calls == 1
slugify("Hello World")   // cached:   "hello-world", calls == 1
slugify("Other Post")    // computes: "other-post",  calls == 2
```

The two-argument overload keys the cache by the argument pair:

```kotlin
val distance = memoize { a: String, b: String -> levenshtein(a, b) }

distance("kitten", "sitting")   // computes
distance("kitten", "sitting")   // cached
```

Facts that matter in production: the cache is unbounded and lives as long as
the returned function, a cached `null` result is returned without recomputing,
arguments are compared by `equals` and `hashCode`, and the returned function is
not thread-safe. For a bounded cache, use an [`LruCache`](#lrucache-a-bounded-cache-for-decoded-images)
with `getOrPut` instead.
