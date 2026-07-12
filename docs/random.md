# Random utilities

Extensions on `kotlin.random.Random` and on collections for generating random text, drawing from distributions, and sampling. Each snippet is copy-pasteable and uses only real APIs from `io.github.yuroyami.kitecore.random`.

## Random strings

### Generate a token from a built-in alphabet

```kotlin
import io.github.yuroyami.kitecore.random.nextAlphanumeric
import kotlin.random.Random

val token = Random.nextAlphanumeric(12)
// e.g. "kX3Tz09aQm2b": exactly 12 characters from a-z, A-Z, 0-9
```

`nextAlphabetic`, `nextDigits`, and `nextHexString` follow the same contract: exactly `length` characters, each chosen independently and uniformly. A length of zero yields the empty string; a negative length throws `IllegalArgumentException`.

```kotlin
import io.github.yuroyami.kitecore.random.nextAlphabetic
import io.github.yuroyami.kitecore.random.nextDigits
import io.github.yuroyami.kitecore.random.nextHexString

val letters = Random.nextAlphabetic(8)  // e.g. "QwErTyUi": a-zA-Z only
val pin     = Random.nextDigits(6)      // e.g. "042917": leading zeros are possible
val color   = Random.nextHexString(6)   // e.g. "3fa2c9": lowercase 0-9a-f
```

### Use a custom alphabet

```kotlin
import io.github.yuroyami.kitecore.random.nextChar
import io.github.yuroyami.kitecore.random.nextString

// Characters that are hard to confuse when read aloud (no 0/O, 1/I/l)
val code = Random.nextString(6, "ABCDEFGHJKMNPQRSTUVWXYZ23456789")

// One character at a time
val suit = Random.nextChar("SHDC")
```

Each character is drawn uniformly from the positions of the alphabet, so a character that occurs twice in the alphabet is twice as likely to appear. The alphabet must not be empty.

### The predefined alphabets

`RandomAlphabets` holds the strings behind the shorthand functions. Pass them anywhere an alphabet is accepted.

| Constant | Contents | Size |
| --- | --- | --- |
| `RandomAlphabets.ALPHANUMERIC` | a-z, A-Z, 0-9 | 62 |
| `RandomAlphabets.ALPHABETIC` | a-z, A-Z | 52 |
| `RandomAlphabets.DIGITS` | 0-9 | 10 |
| `RandomAlphabets.HEX_LOWER` | 0-9, a-f | 16 |

```kotlin
import io.github.yuroyami.kitecore.random.RandomAlphabets

// These two calls are equivalent
Random.nextAlphanumeric(16)
Random.nextString(16, RandomAlphabets.ALPHANUMERIC)
```

## Reproducible output with a seed

Every function on this page is an extension on `kotlin.random.Random` or accepts a `random` parameter, so a seeded instance makes the output deterministic. Useful in tests and anywhere a run must be replayable.

```kotlin
import io.github.yuroyami.kitecore.random.nextAlphanumeric
import io.github.yuroyami.kitecore.random.sample
import kotlin.random.Random

val rng = Random(42)

val id = rng.nextAlphanumeric(10)
// The same 10-character string on every run

val subset = listOf("a", "b", "c", "d", "e").sample(2, random = Random(42))
// The same 2 elements on every run
```

Two `Random(42)` instances produce identical streams, so a test and the code under test can be kept in lockstep by sharing the seed.

## Distributions

### Biased coin flip

```kotlin
import io.github.yuroyami.kitecore.random.nextBoolean

if (Random.nextBoolean(probability = 0.05)) {
    // Runs for roughly 1 in 20 calls
}
```

A probability of `0.0` always yields `false` and `1.0` always yields `true`. Values outside `0.0..1.0`, including NaN, throw `IllegalArgumentException`.

### Normally distributed values

```kotlin
import io.github.yuroyami.kitecore.random.nextGaussian

val latency = Random.nextGaussian(mean = 200.0, standardDeviation = 30.0)
// About 68% of values land within 170.0..230.0
```

Values are produced with the Box-Muller transform. The defaults are `mean = 0.0` and `standardDeviation = 1.0`, the standard normal distribution. A standard deviation of `0.0` always yields the mean; a negative one throws.

### A stream of Gaussian values

```kotlin
import io.github.yuroyami.kitecore.random.gaussianSequence

val noise = Random.gaussianSequence(mean = 0.0, standardDeviation = 0.1)
    .take(100)
    .toList()
```

The sequence is infinite and lazy. Bound it with `take` (or another terminating operator) before collecting, or iteration never ends.

### Random sign

```kotlin
import io.github.yuroyami.kitecore.random.nextSign

val jitter = Random.nextSign() * Random.nextDouble(5.0)
// nextSign() is +1 or -1 with equal probability
```

## Collections

### Sample without replacement

```kotlin
import io.github.yuroyami.kitecore.random.sample

val participants = listOf("Ana", "Bo", "Cid", "Dee", "Eli")
val winners = participants.sample(3)
// 3 elements, no position picked twice, order randomized
```

Every subset of size `n` is equally likely. `n` must be within `0..size`; anything else throws `IllegalArgumentException`. Duplicate values in the source can still appear in the result, since sampling is by position.

`sampleOrNull` returns `null` instead of throwing when `n` is out of range:

```kotlin
import io.github.yuroyami.kitecore.random.sampleOrNull

val winners = participants.sampleOrNull(10) ?: emptyList()
// null: cannot draw 10 distinct positions from 5 elements
```

### Sample with replacement

```kotlin
import io.github.yuroyami.kitecore.random.sampleWithReplacement

val measurements = listOf(9.8, 10.1, 10.0, 9.9)
val bootstrap = measurements.sampleWithReplacement(1000)
// 1000 elements; positions repeat, and n may exceed the collection size
```

An `n` of zero yields an empty list even for an empty collection. A positive `n` on an empty collection throws.

### Weighted choice

```kotlin
import io.github.yuroyami.kitecore.random.weightedRandom
import io.github.yuroyami.kitecore.random.weightedRandomIndex

val tiers = listOf("common", "rare", "epic")
val weights = listOf(80.0, 15.0, 5.0)

val drop = tiers.weightedRandom(weights)
// "common" 80% of the time, "rare" 15%, "epic" 5%

val index = tiers.weightedRandomIndex(weights)
// The index instead of the element: 0, 1, or 2
```

The probability of element `i` is `weights[i] / weights.sum()`, so weights do not need to add up to 1.0. An element with weight `0.0` is never chosen. The weights list must match the list size, every weight must be non-negative and finite, and the total must be strictly positive; violations throw `IllegalArgumentException`.

`weightedRandomOrNull` returns `null` for any of those violations instead of throwing:

```kotlin
import io.github.yuroyami.kitecore.random.weightedRandomOrNull

val safe = tiers.weightedRandomOrNull(listOf(0.0, 0.0, 0.0))
// null: the total weight is not strictly positive
```

### Pick one of a few literals

```kotlin
import io.github.yuroyami.kitecore.random.randomOf
import kotlin.random.Random

val greeting = randomOf("hi", "hey", "hello")
// Each option is equally likely

val seeded = randomOf("red", "green", "blue", random = Random(42))
// The random parameter follows the vararg, so pass it by name
```

At least one option is required; calling `randomOf()` with no options throws `IllegalArgumentException`.
