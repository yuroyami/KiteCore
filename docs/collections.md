# Collection utilities

Walkthroughs of the collection extensions in `io.github.yuroyami.kitecore.collections`. Each snippet is copy-pasteable, uses only real APIs from KiteCore 0.1.0, and shows the expected value in a comment.

One import brings in the whole package:

```kotlin
import io.github.yuroyami.kitecore.collections.*
```

## Positional access

Named accessors for common positions, each with a throwing form and an `OrNull` companion, plus prefix and suffix checks that run in O(k) without allocating sublists.

```kotlin
val podium = listOf("gold", "silver", "bronze", "tin", "wood")

podium.second()       // "silver"
podium.third()        // "bronze"
podium.fourth()       // "tin"
podium.penultimate()  // "tin"
podium.middle()       // "bronze" (even sizes use the earlier middle)

val single = listOf("only")
single.secondOrNull()       // null
single.thirdOrNull()        // null
single.fourthOrNull()       // null
single.penultimateOrNull()  // null
single.middleOrNull()       // "only"

podium.headAndTail()                     // (gold, [silver, bronze, tin, wood])
emptyList<String>().headAndTailOrNull()  // null

listOf("a", "b", "a", "c", "a").indicesOf("a")  // [0, 2, 4]
listOf(1, 2, 3, 4).startsWith(listOf(1, 2))     // true
listOf(1, 2, 3, 4).endsWith(listOf(3, 4))       // true
```

## List surgery

Rearrange, grow, and reshape lists. The past-tense functions (`swapped`, `moved`, `rotated`, ...) return a new list and never touch the receiver; the imperative forms (`swap`, `move`, `rotate`, ...) edit a `MutableList` in place.

```kotlin
val items = listOf("a", "b", "c", "d")

items.swapped(0, 3)                      // [d, b, c, a]
items.moved(fromIndex = 3, toIndex = 0)  // [d, a, b, c]
items.replacedAt(1, "B")                 // [a, B, c, d]
items.padded(6, "-")                     // [a, b, c, d, -, -]
items.rotated(1)                         // [d, a, b, c]
items.splitAt(2)                         // ([a, b], [c, d])

listOf(1, 2).repeated(3)                      // [1, 2, 1, 2, 1, 2]
listOf(1, 3, 5).interleaved(listOf(2, 4, 6))  // [1, 2, 3, 4, 5, 6]
listOf(listOf(1, 2, 3), listOf(4, 5, 6)).transposed()  // [[1, 4], [2, 5], [3, 6]]
listOf("a", "b", "c").zipWithNextCircular()   // [(a, b), (b, c), (c, a)]

// In-place variants for MutableList:
val scores = mutableListOf(10, 20, 30, 40)
scores.swap(0, 3)                        // scores is now [40, 20, 30, 10]
scores.move(fromIndex = 1, toIndex = 3)  // [40, 30, 10, 20]
scores.updateAt(0) { it + 1 }            // [41, 30, 10, 20]
scores.removeFirstWhere { it < 15 }      // true; scores is now [41, 30, 20]
scores.removeLastWhere { it > 100 }      // false; nothing matched
scores.rotate(1)                         // [20, 41, 30]
scores.padTo(5, 0)                       // [20, 41, 30, 0, 0]
```

## Grouping and analysis

Segment sequences by value, count and detect duplicates, and combine collections. Everything preserves encounter order, so the results are stable and predictable.

```kotlin
listOf(1, 1, 2, 2, 2, 1).chunkedBy { it }     // [[1, 1], [2, 2, 2], [1]]
listOf(1, 2, 0, 3, 0, 4).splitBy { it == 0 }  // [[1, 2], [3], [4]]

val tags = listOf("kmp", "kotlin", "kmp", "ios", "kotlin", "kmp")
tags.duplicates()              // [kmp, kotlin]
tags.frequencies()             // {kmp=3, kotlin=2, ios=1}
tags.countBy { it.length }     // {3=4, 6=2}
tags.mode()                    // "kmp"
tags.isDistinct()              // false
tags.anyDuplicate()            // true
tags.takeIfNotEmpty()?.mode()  // "kmp" (null-safe guard for empty collections)

listOf(3, 9, 1, 4).minMaxOrNull()  // (1, 9)
listOf(7, 7, 7).allEqual()         // true

listOf("S", "M").cartesianProduct(listOf("red", "blue"))
// [(S, red), (S, blue), (M, red), (M, blue)]

listOf("a", "b", "c", "d").partitionIndexed { index, _ -> index % 2 == 0 }
// ([a, c], [b, d])

listOf("north", "east", "south").associateWithIndex()         // {north=0, east=1, south=2}
listOf("apple", "avocado", "banana").mapToSet { it.first() }  // [a, b]
```

## Maps

Invert, filter, rewrite, and merge maps. All results are `LinkedHashMap` backed, so entry order follows first occurrence, and the `NotNull` family narrows nullable key or value types for you.

```kotlin
val ports = mapOf("http" to 80, "https" to 443, "ws" to 80)
ports.inverted()     // {80=ws, 443=https} (last key wins per value)
ports.invertedAll()  // {80=[http, ws], 443=[https]} (no key lost)

val overrides: Map<String, Int?> = mapOf("timeout" to 30, "retries" to null)
overrides.filterValuesNotNull()  // {timeout=30}, typed Map<String, Int>

val headers: Map<String?, String> = mapOf("Host" to "example.com", null to "dropped")
headers.filterKeysNotNull()      // {Host=example.com}, typed Map<String, String>

val raw = mapOf("a" to "1", "b" to "x", "c" to "3")
raw.mapValuesNotNull { it.value.toIntOrNull() }                           // {a=1, c=3}
raw.mapKeysNotNull { if (it.value == "x") null else it.key.uppercase() }  // {A=1, C=3}

val base = mapOf("apples" to 2, "pears" to 1)
val extra = mapOf("apples" to 3, "plums" to 5)
base.mergedWith(extra) { _, current, incoming -> current + incoming }
// {apples=5, pears=1, plums=5}

// In-place variant for MutableMap:
val basket = mutableMapOf("apples" to 2)
basket.mergeWith(extra) { _, current, incoming -> maxOf(current, incoming) }
// basket is now {apples=3, plums=5}

emptyMap<String, Int>().takeIfNotEmpty()  // null
```

## Sets

Toggle membership in one call instead of an `if`/`contains` dance: `toggle` mutates a `MutableSet` and reports the state after the call, `toggled` returns a modified copy of a read-only `Set`.

```kotlin
val selection = mutableSetOf("bold")
selection.toggle("italic")  // true; selection is now [bold, italic]
selection.toggle("bold")    // false; selection is now [italic]

val flags = setOf("wifi", "gps")
flags.toggled("gps")  // [wifi]
flags.toggled("nfc")  // [wifi, gps, nfc]
```

## Statistics

Median, population variance, standard deviation, and interpolated percentiles, each with an `OrNull` form for possibly empty data. Every statistic accepts `List<Int>`, `List<Long>`, and `List<Double>`; the integer overloads widen to `Double` first, and `product` covers `Iterable<Int>`, `Iterable<Long>`, and `Iterable<Double>`.

```kotlin
val latencies = listOf(120.0, 150.0, 480.0, 110.0)
latencies.median()  // 135.0 (mean of the two middle values)

val samples = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
samples.variance()  // 4.0 (population variance, divisor n)
samples.stdDev()    // 2.0

val ms = listOf(100, 200, 300, 400, 500)  // Int overload, same call sites
ms.percentile(50.0)  // 300.0
ms.percentile(90.0)  // 460.0 (linear interpolation between ranks)

emptyList<Double>().medianOrNull()          // null
emptyList<Int>().varianceOrNull()           // null
emptyList<Long>().stdDevOrNull()            // null
emptyList<Double>().percentileOrNull(99.0)  // null

listOf(2, 3, 4).product()     // 24 (accumulated as Long)
listOf(2L, 3L, 4L).product()  // 24
listOf(0.5, 8.0).product()    // 4.0
```

## Reference

Alphabetical index of every public API in the package. Statistics rows cover the `Int`, `Long`, and `Double` overloads of the same name.

| API | Description |
| --- | --- |
| `allEqual()` | True when every element equals the first; empty is true. |
| `anyDuplicate()` | True when at least one element repeats. |
| `associateWithIndex()` | Maps each element to its index; the last occurrence wins. |
| `cartesianProduct(other)` | Every pairing of receiver elements with `other` elements. |
| `chunkedBy(selector)` | Consecutive runs, split whenever the selector value changes. |
| `countBy(selector)` | Counts elements per selector value, ordered by first occurrence. |
| `duplicates()` | Set of elements occurring more than once. |
| `endsWith(suffix)` | True when the list ends with the given elements in order. |
| `filterKeysNotNull()` | Drops entries with null keys; result keys are typed non-null. |
| `filterValuesNotNull()` | Drops entries with null values; result values are typed non-null. |
| `fourth()` | Fourth element, throwing when absent. |
| `fourthOrNull()` | Fourth element, or null. |
| `frequencies()` | Map of each distinct element to its occurrence count. |
| `headAndTail()` | First element paired with the rest, throwing when empty. |
| `headAndTailOrNull()` | First element paired with the rest, or null when empty. |
| `indicesOf(element)` | Every index where the element occurs, ascending. |
| `interleaved(other)` | Alternates elements of two lists, appending the leftover tail. |
| `inverted()` | Value-to-key map; the last key wins on value collisions. |
| `invertedAll()` | Value to list-of-keys map; no key is lost. |
| `isDistinct()` | True when no element repeats; short-circuits on the first repeat. |
| `mapKeysNotNull(transform)` | Rewrites keys, dropping entries where the transform returns null. |
| `mapToSet(transform)` | Set of transformed elements, ordered by first occurrence. |
| `mapValuesNotNull(transform)` | Rewrites values, dropping entries where the transform returns null. |
| `median()` | Median of a list; `Int`, `Long`, and `Double` overloads. |
| `medianOrNull()` | Median, or null when empty; `Int`, `Long`, and `Double` overloads. |
| `mergedWith(other, resolve)` | Copy combining two maps, resolving key collisions via the lambda. |
| `mergeWith(other, resolve)` | In-place merge into a `MutableMap`, resolving key collisions. |
| `middle()` | Middle element, throwing when empty; even sizes use the earlier middle. |
| `middleOrNull()` | Middle element, or null when empty. |
| `minMaxOrNull()` | Smallest and largest element as a pair in one pass, or null. |
| `mode()` | Most frequent element, or null when empty; first encountered wins ties. |
| `move(fromIndex, toIndex)` | Moves an element of a `MutableList` in place, shifting those between. |
| `moved(fromIndex, toIndex)` | Copy with one element moved to a new index. |
| `padded(size, element)` | Copy grown to `size` by appending the element. |
| `padTo(size, element)` | Grows a `MutableList` in place to `size` by appending the element. |
| `partitionIndexed(predicate)` | Splits into matching and non-matching lists; the predicate sees the index. |
| `penultimate()` | Element before the last, throwing when absent. |
| `penultimateOrNull()` | Element before the last, or null. |
| `percentile(p)` | Interpolated percentile for `p` in 0.0..100.0; `Int`, `Long`, and `Double` overloads. |
| `percentileOrNull(p)` | Percentile, or null when empty; `Int`, `Long`, and `Double` overloads. |
| `product()` | Product of all elements; `Int` and `Long` receivers return `Long`, `Double` returns `Double`; empty yields 1. |
| `removeFirstWhere(predicate)` | Removes the first match from a `MutableList`, reporting success. |
| `removeLastWhere(predicate)` | Removes the last match from a `MutableList`, reporting success. |
| `repeated(times)` | List with the receiver's elements repeated `times` times in sequence. |
| `replacedAt(index, element)` | Copy with the element at `index` replaced. |
| `rotate(distance)` | Rotates a `MutableList` in place; any distance is normalized. |
| `rotated(distance)` | Copy rotated by `distance` positions; any distance is normalized. |
| `second()` | Second element, throwing when absent. |
| `secondOrNull()` | Second element, or null. |
| `splitAt(index)` | Elements before `index` paired with elements from `index` onward. |
| `splitBy(isDelimiter)` | Segments between delimiter elements, dropping the delimiters. |
| `startsWith(prefix)` | True when the list begins with the given elements in order. |
| `stdDev()` | Population standard deviation; `Int`, `Long`, and `Double` overloads. |
| `stdDevOrNull()` | Standard deviation, or null when empty; `Int`, `Long`, and `Double` overloads. |
| `swap(i, j)` | Exchanges two elements of a `MutableList` in place. |
| `swapped(i, j)` | Copy with two elements exchanged. |
| `takeIfNotEmpty()` | The receiver, or null when empty; available on `Collection` and `Map`. |
| `third()` | Third element, throwing when absent. |
| `thirdOrNull()` | Third element, or null. |
| `toggle(element)` | Adds or removes in a `MutableSet`; true when present afterwards. |
| `toggled(element)` | Copy of a `Set` with the element added if absent or removed if present. |
| `transposed()` | Rows become columns; all rows must share one size. |
| `updateAt(index, transform)` | Replaces one element of a `MutableList` with a transform of itself. |
| `variance()` | Population variance; `Int`, `Long`, and `Double` overloads. |
| `varianceOrNull()` | Variance, or null when empty; `Int`, `Long`, and `Double` overloads. |
| `zipWithNextCircular()` | Each element paired with its successor, wrapping to the first. |
