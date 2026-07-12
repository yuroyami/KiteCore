# Numbers and math

Rounding, formatting, interpolation, integer arithmetic, bit flags, and size
constants from `io.github.yuroyami.kitecore.math`. Every snippet is
copy-pasteable, uses only real APIs from KiteCore 0.1.0, and prints the output
shown in the comments. The samples assume:

```kotlin
import io.github.yuroyami.kitecore.math.*
```

## Rounding and formatting

### Round, floor, or ceil at a decimal place

`roundTo` rounds half-up with ties away from zero. `floorTo` always rounds
toward negative infinity, `ceilTo` toward positive infinity. All three accept
`decimals` in `0..15` and throw `IllegalArgumentException` outside that range.
NaN, infinities, and magnitudes at or above 2^52 are returned unchanged.

```kotlin
println(3.14159.roundTo(2))    // 3.14
println(2.5.roundTo(0))        // 3.0
println((-2.5).roundTo(0))     // -3.0
println(2.5f.roundTo(0))       // 3.0

println(3.75.floorTo(1))       // 3.7
println((-3.11).floorTo(1))    // -3.2
println(3.25.ceilTo(1))        // 3.3
println((-3.75).ceilTo(1))     // -3.7
```

The result is the nearest representable `Double`, so tiny binary
representation error can remain. When you need an exact decimal string, format
instead of rounding.

### Format with a fixed number of decimals

`formatDecimals` is locale-independent: the separator is always `'.'` and
rounding is half-up with ties away from zero. Carries propagate across the
decimal point, and values that round to zero print without a sign.

```kotlin
println(3.14159.formatDecimals(3))   // 3.142
println(9.99.formatDecimals(1))      // 10.0
println(9.99f.formatDecimals(1))     // 10.0
println(2.0.formatDecimals(2))       // 2.00
println((-1.5).formatDecimals(0))    // -2
println((-0.004).formatDecimals(2))  // 0.00
```

NaN and infinities format as `"NaN"`, `"Infinity"`, and `"-Infinity"`. The
function throws when the magnitude scaled by 10^decimals exceeds
`Long.MAX_VALUE`.

### Snap to the nearest step

`roundToNearest` returns the closest multiple of a step, with ties away from
zero. The `Double` overload requires a finite positive step; the `Int` overload
computes in `Long` and throws `ArithmeticException` when the rounded result
does not fit back in `Int`.

```kotlin
println(1.25.roundToNearest(0.5))     // 1.5
println((-1.25).roundToNearest(0.5))  // -1.5
println(0.7.roundToNearest(0.25))     // 0.75

println(25.roundToNearest(10))        // 30
println((-25).roundToNearest(10))     // -30
println(7.roundToNearest(5))          // 5
```

### Group digits for display

`formatGrouped` inserts a separator between each group of three digits. The
grouping is fixed and locale-independent. `Int.MIN_VALUE` and `Long.MIN_VALUE`
are handled without overflow.

```kotlin
println(1_234_567L.formatGrouped())        // 1,234,567
println(1_234_567.formatGrouped())         // 1,234,567
println((-9_876_543L).formatGrouped(' '))  // -9 876 543
```

### Format byte counts

`formatBytes` picks the largest unit that fits. The default base is 1024 with
units B, KiB, MiB, GiB, TiB, PiB, EiB; pass `si = true` for base 1000 with
units B, kB, MB, GB, TB, PB, EB. Plain byte values print without decimals;
scaled values use `formatDecimals`.

```kotlin
println(1536L.formatBytes())             // 1.5 KiB
println(1536L.formatBytes(si = true))    // 1.5 kB
println(512L.formatBytes())              // 512 B
println(1_073_741_824L.formatBytes(2))   // 1.00 GiB
println((-2048L).formatBytes())          // -2.0 KiB
```

Because the value is rounded after unit selection, a count sitting close under
a unit boundary can print as the base of the next unit:

```kotlin
println(1_048_571L.formatBytes())        // 1024.0 KiB
```

### Format a fraction as a percentage

`formatPercent` multiplies by 100 and formats with `formatDecimals`, so the
same half-up rounding and `0..15` decimals range apply.

```kotlin
println(0.42.formatPercent())     // 42%
println(0.075.formatPercent(1))   // 7.5%
println(1.0.formatPercent())      // 100%
```

## Interpolation

`lerp` blends between two endpoints; `inverseLerp` recovers the fraction;
`remap` chains the two to translate a value from one range to another. None of
them clamp, so fractions outside `0..1` extrapolate.

```kotlin
println(lerp(0.0, 10.0, 0.25))   // 2.5
println(lerp(0.0, 10.0, 1.5))    // 15.0
println(lerp(0f, 100f, 0.25f))   // 25.0

println(inverseLerp(10.0, 20.0, 15.0))  // 0.5

// Celsius to Fahrenheit is a range remap.
println(25.0.remap(0.0, 100.0, 32.0, 212.0))  // 77.0
```

`inverseLerp` returns `0.0` when both endpoints are equal, and `remap` returns
`toStart` in that case, so neither divides by zero.

When you do want clamping, apply `clamp01` to the fraction first:

```kotlin
println(1.4.clamp01())     // 1.0
println((-0.2).clamp01())  // 0.0
println(0.6f.clamp01())    // 0.6
```

NaN stays NaN through `clamp01`.

## Integer math

### Parity

```kotlin
println(4.isEven())   // true
println(7.isOdd())    // true
println(4L.isEven())  // true
println(7L.isOdd())   // true
```

### Digits

`digits` returns the decimal digits of the absolute value, most significant
first. `digitCount` counts them. Both discard the sign and handle
`Int.MIN_VALUE` and `Long.MIN_VALUE` without overflow.

```kotlin
println((-305).digits())                 // [3, 0, 5]
println(0.digits())                      // [0]
println(12345.digitCount())              // 5
println((-9_876_543_210L).digitCount())  // 10
```

### GCD and LCM

Signs are ignored and results are never negative. `gcd(0, 0)` is `0` and `lcm`
returns `0L` when either argument is zero.

```kotlin
println(gcd(12, 18))     // 6
println(gcd(48L, 180L))  // 12
println(lcm(4, 6))       // 12
println(lcm(6L, 15L))    // 30
```

Overflow behavior is documented per overload: `gcd(Int, Int)` throws
`ArithmeticException` only when the result equals 2^31 (for example
`gcd(Int.MIN_VALUE, 0)`), and `gcd(Long, Long)` only when it equals 2^63.
`lcm(Int, Int)` returns `Long` and never throws; `lcm(Long, Long)` throws
`ArithmeticException` when the result does not fit in `Long`.

### Powers and factorials

`Int.pow` uses exponentiation by squaring with checked multiplication, so any
`Long` overflow throws `ArithmeticException` instead of wrapping. Negative
exponents throw `IllegalArgumentException`. `factorial` is defined for
`0..20`; `20!` is the largest factorial that fits in a `Long`.

```kotlin
println(2.pow(10))      // 1024
println(2.pow(62))      // 4611686018427387904
println(0.pow(0))       // 1

println(factorial(5))   // 120
println(factorial(20))  // 2432902008176640000
```

### Primality

`isPrime` uses deterministic trial division in O(sqrt(n)). Values below 2,
including all negatives, return `false`.

```kotlin
println(isPrime(97L))         // true
println(isPrime(1L))          // false
println(isPrime(1_000_003L))  // true
```

### Powers of two

Zero and negative values are never powers of two. `nextPowerOfTwo` returns `1`
for values at or below 1 and throws `ArithmeticException` above 2^30
(1073741824), the largest power of two representable in `Int`.

```kotlin
println(16.isPowerOfTwo())      // true
println(0.isPowerOfTwo())       // false
println(1L.isPowerOfTwo())      // true

println(17.nextPowerOfTwo())    // 32
println(1024.nextPowerOfTwo())  // 1024
println(0.nextPowerOfTwo())     // 1
```

### Midpoint without overflow

`midpoint` computes `this + (other - this) / 2`, which matches `(a + b) / 2`
on in-range inputs but stays correct near the type limits. Odd differences
round toward the receiver.

```kotlin
println(100.midpoint(200))  // 150
println(0.midpoint(5))      // 2
println(5.midpoint(0))      // 3

println(Int.MAX_VALUE.midpoint(Int.MAX_VALUE - 2))  // 2147483646
println(4_000_000_000L.midpoint(5_000_000_000L))    // 4500000000
```

### Ordinals

The suffix is computed from the absolute value, and 11, 12, 13 endings
correctly yield "th".

```kotlin
println(1.ordinalSuffix())        // st
println(12.ordinalSuffix())       // th
println((-2).ordinalSuffix())     // nd
println(23.withOrdinalSuffix())   // 23rd
println(111.withOrdinalSuffix())  // 111th
```

## Angles and comparison

### Degrees and radians

```kotlin
import kotlin.math.PI

println(180.0.toRadians())     // 3.141592653589793
println(90.0.toRadians())      // 1.5707963267948966
println((PI / 2).toDegrees())  // 90.0
```

### Approximate equality

Comparing floating point results with `==` fails on accumulated
representation error; `isApproximately` compares within an epsilon. Defaults
are `1e-9` for `Double` and `1e-6f` for `Float`. Exactly equal values,
including matching infinities, return `true`; anything involving NaN returns
`false`.

```kotlin
val sum = 0.1 + 0.2
println(sum)                        // 0.30000000000000004
println(sum == 0.3)                 // false
println(sum.isApproximately(0.3))   // true

println(1.0000001f.isApproximately(1f))  // true
println(1.001f.isApproximately(1f))      // false
```

## Bit flags

`hasFlag` tests that every bit of the flag is set, so it works for multi-bit
flags too. A flag of zero always returns `true` because the empty bit set is
contained in every mask. `withFlag` and `withoutFlag` are idempotent;
`toggleFlag` applied twice restores the original mask.

```kotlin
const val BOLD = 1
const val ITALIC = 2
const val UNDERLINE = 4

var style = 0
style = style.withFlag(BOLD).withFlag(UNDERLINE)
println(style)                             // 5
println(style.hasFlag(BOLD))               // true
println(style.hasFlag(ITALIC))             // false
println(style.hasFlag(BOLD or UNDERLINE))  // true
println(style.hasFlag(0))                  // true

style = style.withoutFlag(UNDERLINE)
println(style)                             // 1
style = style.toggleFlag(ITALIC)
println(style)                             // 3
style = style.toggleFlag(ITALIC)
println(style)                             // 1
```

## Size constants

`KB`, `MB`, and `GB` are decimal (base 1000); `KiB`, `MiB`, and `GiB` are
binary (base 1024). Each property multiplies an `Int` receiver into a `Long`
byte count, so the result cannot overflow for any `Int` value.

```kotlin
println(3.KB)   // 3000
println(2.MB)   // 2000000
println(2.GB)   // 2000000000
println(4.KiB)  // 4096
println(8.MiB)  // 8388608
println(1.GiB)  // 1073741824
```

They pair naturally with `formatBytes`:

```kotlin
println(8.MiB.formatBytes())          // 8.0 MiB
println(2.MB.formatBytes(si = true))  // 2.0 MB
```

## Percentages

`percentOf` answers "what percentage of the total is this value". It returns
`0.0` when the total is zero, so callers never divide by zero.

```kotlin
println(25.percentOf(50))   // 50.0
println(30.percentOf(120))  // 25.0
println(5.percentOf(0))     // 0.0
```

## Reference

Every public declaration in the package, alphabetical. Overloads share a row.

| Declaration | Summary |
| --- | --- |
| `Double.ceilTo(decimals)` | Round up (toward positive infinity) at `decimals` fractional digits. |
| `Double.clamp01()`, `Float.clamp01()` | Clamp into `0..1`; NaN stays NaN. |
| `Int.digitCount()`, `Long.digitCount()` | Number of decimal digits in the absolute value; zero counts as one. |
| `Int.digits()` | Decimal digits of the absolute value, most significant first. |
| `factorial(n)` | Factorial for `n` in `0..20`; throws outside that range. |
| `Double.floorTo(decimals)` | Round down (toward negative infinity) at `decimals` fractional digits. |
| `Long.formatBytes(decimals, si)` | Human-readable size string, base 1024 or base 1000. |
| `Double.formatDecimals(decimals)`, `Float.formatDecimals(decimals)` | Fixed-decimals string, half-up, locale-independent. |
| `Long.formatGrouped(separator)`, `Int.formatGrouped(separator)` | Decimal string with a separator every three digits. |
| `Double.formatPercent(decimals)` | Multiply by 100, format, append `%`. |
| `Int.GB` | Gigabytes (1000^3) as a `Long` byte count. |
| `gcd(a, b)` (`Int`, `Long`) | Greatest common divisor; never negative; throws only when the result equals 2^31 or 2^63. |
| `Int.GiB` | Gibibytes (1024^3) as a `Long` byte count. |
| `Int.hasFlag(flag)` | True when every bit of `flag` is set; `hasFlag(0)` is always true. |
| `inverseLerp(start, stop, value)` | Fraction of `value` between the endpoints; `0.0` when they are equal. |
| `Double.isApproximately(other, epsilon)`, `Float.isApproximately(other, epsilon)` | Equality within an epsilon; false for NaN. |
| `Int.isEven()`, `Long.isEven()` | True when divisible by two. |
| `Int.isOdd()`, `Long.isOdd()` | True when not divisible by two. |
| `Int.isPowerOfTwo()`, `Long.isPowerOfTwo()` | True for positive powers of two only. |
| `isPrime(n)` | Deterministic primality test; values below 2 return false. |
| `Int.KB` | Kilobytes (1000) as a `Long` byte count. |
| `Int.KiB` | Kibibytes (1024) as a `Long` byte count. |
| `lcm(a, b)` (`Int`, `Long`) | Least common multiple as `Long`; the `Int` overload never throws. |
| `lerp(start, stop, fraction)` (`Double`, `Float`) | Linear interpolation; fractions are not clamped. |
| `Int.MB` | Megabytes (1000^2) as a `Long` byte count. |
| `Int.MiB` | Mebibytes (1024^2) as a `Long` byte count. |
| `Int.midpoint(other)`, `Long.midpoint(other)` | Midpoint without intermediate overflow; rounds toward the receiver. |
| `Int.nextPowerOfTwo()` | Smallest power of two at or above this value; throws above 2^30. |
| `Int.ordinalSuffix()` | "st", "nd", "rd", or "th", computed from the absolute value. |
| `Int.percentOf(total)` | Percentage of `total`; `0.0` when `total` is zero. |
| `Int.pow(exponent)` | Checked integer power as `Long`; overflow throws instead of wrapping. |
| `Double.remap(fromStart, fromEnd, toStart, toEnd)` | Translate from one range to another; no clamping. |
| `Double.roundTo(decimals)`, `Float.roundTo(decimals)` | Half-up rounding, ties away from zero, at `decimals` digits. |
| `Double.roundToNearest(step)`, `Int.roundToNearest(step)` | Closest multiple of `step`; ties away from zero. |
| `Double.toDegrees()` | Radians to degrees. |
| `Int.toggleFlag(flag)` | Invert every bit of `flag` in the mask. |
| `Double.toRadians()` | Degrees to radians. |
| `Int.withFlag(flag)` | Set every bit of `flag`; idempotent. |
| `Int.withOrdinalSuffix()` | The number followed by its ordinal suffix, such as "21st". |
| `Int.withoutFlag(flag)` | Clear every bit of `flag`; idempotent. |
