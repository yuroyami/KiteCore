# String formatting

A portable, JVM-style `String.format` for common code, from the `io.github.yuroyami.kitecore.text` package. Each snippet is copy-pasteable and shows the expected value in a comment.

One import brings in both call shapes:

```kotlin
import io.github.yuroyami.kitecore.text.format
```

!!! warning "Do not skip the import"
    Without the import, common code does not compile. JVM code is worse: it compiles
    against the JVM's own `String.format`, which follows the device locale.

## JVM-style string formatting

Use Java's familiar formatter syntax from `commonMain`, without an
`expect`/`actual` wrapper:

```kotlin
String.format("%2\$s: %1\$,08d", 12_345, "items")  // "items: 0012,345"
"%-10s %04d".format("retry", 7)                    // "retry      0007"
"load: %.1f%%%n".format(82.75)                     // "load: 82.8%\n"
```

The first form is a `String.Companion` extension:
`String.format(format, vararg args)`. The second treats the receiver as the
format string: `formatString.format(vararg args)`. Both use the same
pure-common formatter and support argument indexes (`%2$s`), previous-argument
reuse (`%<s`), flags, width, and precision.

The portable conversion surface for non-null arguments is:

| Conversions | Accepted values |
| --- | --- |
| `%b`, `%B` | Any value: a `Boolean` keeps its value and every other value is true. |
| `%h`, `%H` | Any value through its Kotlin `hashCode`. |
| `%s`, `%S` | Any value through `toString`. |
| `%c`, `%C` | `Char`, or a valid Unicode code point supplied as `Byte`, `Short`, or `Int`. |
| `%d`, `%o`, `%x`, `%X` | `Byte`, `Short`, `Int`, and `Long`. |
| `%e`, `%E`, `%f`, `%g`, `%G`, `%a`, `%A` | `Float` and `Double`. |
| `%%`, `%n` | A literal percent sign and a line feed (`\n`), respectively; neither consumes an argument. |

Every argument-taking conversion also accepts `null`: `%b`/`%B` produce
`false`/`FALSE`; the others produce `null`, or `NULL` for an uppercase
conversion. Kotlin unsigned numbers are not silently reinterpreted by numeric
conversions; convert them to a supported signed type explicitly when that is
what you intend.

Formatting is locale-independent across targets: decimal punctuation,
grouping, case conversion, and special floating-point values follow ROOT-like
rules rather than the device's current locale. `%n` is always LF (`\n`), not a
platform-dependent line separator. `%S`, `%C`, and other uppercase conversions
use each target runtime's Unicode casing data. Established characters behave
consistently, but a character added in a newer Unicode release can differ from
an older JDK's `Locale.ROOT` result until their Unicode versions align.

Some JS and Wasm runtimes erase runtime distinctions among numeric types after
values enter `vararg Any?`. KiteCore detects the identities that remain rather
than assuming a target: an ambiguous small integral value uses `Int` width for
`%o`/`%x`, and an ambiguous floating value uses `Double` semantics. If the
integral and floating families themselves share one runtime identity,
conversion-directed formatting also accepts an integral-valued number with
`%f`. JVM, Android, and Native keep their runtime numeric tags and enforce the
type matrix exactly. Use `Long` when an explicit 64-bit integral representation
is required across targets.

This is the portable part of Java's `Formatter` contract, not an emulation of
Java's type system. Java-only values such as `BigInteger`, `BigDecimal`,
`Calendar`, and `Formattable` are not supported, and neither are date/time
conversions (`%t` and `%T`), whose JVM results depend on Java date/time types and
the host time zone. Invalid specifiers, incompatible flags, missing arguments,
and values of the wrong type throw `StringFormatException`, a single
`IllegalArgumentException` subtype rather than Java's platform-only family of
formatter exceptions. `%s` and `%h` can only be as portable as a custom value's
own `toString` and `hashCode` implementations. This also applies to numbers on
erased runtimes: `%s` and `%h` follow that runtime's boxed-number methods, which
can differ from Java wrapper output. Use the dedicated numeric conversions when
JVM-style numeric rendering is required.

## Reference

| API | Description |
| --- | --- |
| `format(vararg args)` | Formats the receiver with JVM-style portable formatter syntax. |
| `String.format(format, vararg args)` | Formats `format` through the `String.Companion` extension. |
| `StringFormatException` | Reports malformed formats, missing arguments, incompatible flags, and value-type mismatches. |
