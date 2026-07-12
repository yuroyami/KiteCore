# Encoding and binary

Hex and base64 codecs, non-cryptographic hashes, byte array searching, and
endian-aware binary reads and writes from
`io.github.yuroyami.kitecore.encoding`. Every snippet is copy-pasteable, uses
only real APIs from KiteCore 0.1.0, and prints the output shown in the
comments. The samples assume:

```kotlin
import io.github.yuroyami.kitecore.encoding.*
```

## Hex

`toHex` emits exactly two digits per byte, most significant nibble first, so
the output is always twice the input length. Lowercase is the default.

```kotlin
val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
println(bytes.toHex())                  // deadbeef
println(bytes.toHex(upperCase = true))  // DEADBEEF
```

`decodeHex` accepts lowercase and uppercase digits in any mix and throws
`IllegalArgumentException` on an odd-length string or an invalid character.

```kotlin
println("KiteCore".encodeToByteArray().toHex())        // 4b697465436f7265
println("4b697465436f7265".decodeHex().decodeToString())  // KiteCore
println("48656C6C6F".decodeHex().decodeToString())        // Hello

"abc".decodeHex()  // throws IllegalArgumentException: odd length
```

## Base64

Two alphabets, two padding policies:

- `toBase64` uses the standard alphabet of RFC 4648 section 4 (`A-Z a-z 0-9 + /`)
  and always pads the output with `=` to a multiple of four characters.
- `toBase64Url` uses the url-safe alphabet of RFC 4648 section 5
  (`A-Z a-z 0-9 - _`) and never pads, which makes the output safe for URLs and
  file names.

```kotlin
println("Hi".encodeToByteArray().toBase64())              // SGk=
println("Hello, world".encodeToByteArray().toBase64())    // SGVsbG8sIHdvcmxk
println("Hi".encodeToByteArray().toBase64Url())           // SGk

// Bytes that hit the two alphabet-specific characters:
val edge = byteArrayOf(0xFB.toByte(), 0xEF.toByte(), 0xBE.toByte())
println(edge.toBase64())     // ++++
println(edge.toBase64Url())  // ----
```

Both decoders accept padded and unpadded input. When padding is present, the
total length must be a multiple of four and at most two `=` characters may
appear, only at the end. A data-character count with a remainder of one when
divided by four is rejected. Each decoder rejects characters outside its own
alphabet, so `decodeBase64Url` rejects `+` and `/`.

```kotlin
println("SGVsbG8=".decodeBase64().decodeToString())  // Hello
println("SGk".decodeBase64().decodeToString())       // Hi
println("SGk=".decodeBase64Url().decodeToString())   // Hi

"++++".decodeBase64Url()  // throws IllegalArgumentException: invalid character
```

Trailing bits that do not form a full byte are discarded without validation,
so non-canonical final characters decode without error.

## Hashes

Both algorithms are non-cryptographic. They are for error detection, hash
tables, and fast fingerprinting; do not use them for integrity against
tampering or any other security purpose.

`crc32` is the IEEE 802.3 variant used by zip and PNG. The unsigned 32-bit
result is returned in the low 32 bits of a `Long`, so it is always in
`0..4294967295` and never negative. An empty input hashes to 0.

```kotlin
println("hello".crc32())                      // 907060870
println(crc32("hello".encodeToByteArray()))   // 907060870
println(crc32(byteArrayOf()))                 // 0
```

`fnv1a32` and `fnv1a64` return the raw bit pattern in an `Int` or `Long`, so
the value may be negative. An empty input hashes to the offset basis of the
algorithm. The `String` receivers hash the UTF-8 bytes.

```kotlin
println("hello".fnv1a32())                     // 1335831723
println(fnv1a32(byteArrayOf()))                // -2128831035

println("hello".fnv1a64())                     // -6615550055289275125
println(fnv1a64(byteArrayOf()))                // -3750763034362895579
```

## Byte operations

### Repeating-key xor

`xor` applies the key cyclically and returns a new array; the receiver is not
modified. Applying the same key twice restores the original bytes. An empty
key throws `IllegalArgumentException`.

```kotlin
val data = "secret".encodeToByteArray()
val key = byteArrayOf(0x2A)

val masked = data.xor(key)
println(masked.toHex())                    // 594f49584f5e
println(masked.xor(key).decodeToString())  // secret
```

### Search and affix tests

`indexOf` finds a byte sequence, `startsWith` and `endsWith` test the ends of
an array. None of them throw: a negative `fromIndex` is treated as 0, an empty
argument always matches, and an argument longer than the array never matches.

```kotlin
val bytes = byteArrayOf(1, 2, 3, 4, 2, 3)
println(bytes.indexOf(byteArrayOf(2, 3)))                // 1
println(bytes.indexOf(byteArrayOf(2, 3), fromIndex = 2)) // 4
println(bytes.indexOf(byteArrayOf(9)))                   // -1

val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
println(png.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))  // true
println(png.endsWith(byteArrayOf(0x1A, 0x0A)))                         // true
```

## Binary reads and writes

Every read and write comes in a little-endian (`Le`) and a big-endian (`Be`)
variant. Little-endian stores the least significant byte at the lowest index;
big-endian stores the most significant byte at the lowest index. That is the
only difference between the variants. All of them validate the range and
throw `IndexOutOfBoundsException` when `offset` is negative or the value would
not fit before the end of the array. Writes modify the array in place and
touch no other indices.

### Reads

```kotlin
val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
println(bytes.readIntBe(0))    // 305419896   (0x12345678)
println(bytes.readIntLe(0))    // 2018915346  (0x78563412)
println(bytes.readShortBe(0))  // 4660        (0x1234)
println(bytes.readShortLe(0))  // 13330       (0x3412)

val eight = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
println(eight.readLongBe(0))   // 72623859790382856    (0x0102030405060708)
println(eight.readLongLe(0))   // 578437695752307201   (0x0807060504030201)
```

The floating point reads decode the integer pattern and reinterpret it through
`Float.fromBits` or `Double.fromBits`, so the exact bit pattern is preserved,
including NaN payloads.

```kotlin
println("3f800000".decodeHex().readFloatBe(0))           // 1.0
println("0000803f".decodeHex().readFloatLe(0))           // 1.0
println("4004000000000000".decodeHex().readDoubleBe(0))  // 2.5
println("0000000000000440".decodeHex().readDoubleLe(0))  // 2.5
```

### Writes

```kotlin
val buf = ByteArray(4)
buf.writeIntBe(0, 0x12345678)
println(buf.toHex())  // 12345678
buf.writeIntLe(0, 0x12345678)
println(buf.toHex())  // 78563412

val two = ByteArray(2)
two.writeShortBe(0, 0x1234)
println(two.toHex())  // 1234
two.writeShortLe(0, 0x1234)
println(two.toHex())  // 3412

val eight = ByteArray(8)
eight.writeLongLe(0, 1L)
println(eight.toHex())  // 0100000000000000
eight.writeLongBe(0, 0x0102030405060708L)
println(eight.toHex())  // 0102030405060708
```

The floating point writes convert with `Float.toRawBits` or
`Double.toRawBits`, preserving the exact bit pattern.

```kotlin
val f = ByteArray(4)
f.writeFloatBe(0, 1.0f)
println(f.toHex())  // 3f800000
f.writeFloatLe(0, 1.0f)
println(f.toHex())  // 0000803f

val d = ByteArray(8)
d.writeDoubleBe(0, 2.5)
println(d.toHex())  // 4004000000000000
d.writeDoubleLe(0, 2.5)
println(d.toHex())  // 0000000000000440
```

## Conversions

The `toByteArrayLe` and `toByteArrayBe` family allocates a new array of the
exact size (2 for `Short`, 4 for `Int` and `Float`, 8 for `Long` and
`Double`) and writes the value at offset 0. These never throw.

```kotlin
println(0x1234.toShort().toByteArrayBe().toHex())     // 1234
println(0x1234.toShort().toByteArrayLe().toHex())     // 3412
println(0x12345678.toByteArrayBe().toHex())           // 12345678
println(0x12345678.toByteArrayLe().toHex())           // 78563412
println(0x0102030405060708L.toByteArrayBe().toHex())  // 0102030405060708
println(1L.toByteArrayLe().toHex())                   // 0100000000000000
println(1.0f.toByteArrayBe().toHex())                 // 3f800000
println(1.0f.toByteArrayLe().toHex())                 // 0000803f
println(2.5.toByteArrayBe().toHex())                  // 4004000000000000
println(2.5.toByteArrayLe().toHex())                  // 0000000000000440
```

## Walkthrough: build and parse a binary record

A small record format: a 4-byte signature, a big-endian version, a big-endian
payload length, the payload, and a CRC-32 of the payload. This is the shape of
many real file and network formats.

```
offset  size  field
0       4     signature "KITE"
4       2     version (Short, big-endian)
6       4     payload length (Int, big-endian)
10      n     payload
10+n    4     crc32 of payload (unsigned Int, big-endian)
```

Building writes each field at its offset:

```kotlin
val TAG = "KITE".encodeToByteArray()

fun buildRecord(version: Short, payload: ByteArray): ByteArray {
    val record = ByteArray(4 + 2 + 4 + payload.size + 4)
    TAG.copyInto(record, 0)
    record.writeShortBe(4, version)
    record.writeIntBe(6, payload.size)
    payload.copyInto(record, 10)
    record.writeIntBe(10 + payload.size, crc32(payload).toInt())
    return record
}

val record = buildRecord(version = 2, payload = "hello".encodeToByteArray())
println(record.toHex())
// 4b49544500020000000568656c6c6f3610a686
```

Parsing reverses each step. The stored checksum was written as a truncated
`Int`, so mask it back to the unsigned range before comparing with `crc32`:

```kotlin
fun parseRecord(record: ByteArray): String {
    require(record.startsWith(TAG)) { "Not a KITE record" }
    val version = record.readShortBe(4)
    val length = record.readIntBe(6)
    val payload = record.copyOfRange(10, 10 + length)
    val stored = record.readIntBe(10 + length).toLong() and 0xFFFFFFFFL
    require(stored == crc32(payload)) { "Checksum mismatch" }
    return "v$version: ${payload.decodeToString()}"
}

println(parseRecord(record))  // v2: hello
```

## Reference

Every public declaration in the package, alphabetical. Overloads share a row.

| Declaration | Summary |
| --- | --- |
| `crc32(bytes)`, `String.crc32()` | IEEE CRC-32 as an unsigned value in a `Long`; non-cryptographic. |
| `String.decodeBase64()` | Decode standard base64; padded or unpadded input. |
| `String.decodeBase64Url()` | Decode url-safe base64; rejects `+` and `/`. |
| `String.decodeHex()` | Decode hex, mixed case accepted; throws on odd length or invalid digits. |
| `ByteArray.endsWith(suffix)` | True when the array ends with the given bytes; never throws. |
| `fnv1a32(bytes)`, `String.fnv1a32()` | 32-bit FNV-1a hash as a raw `Int` pattern; non-cryptographic. |
| `fnv1a64(bytes)`, `String.fnv1a64()` | 64-bit FNV-1a hash as a raw `Long` pattern; non-cryptographic. |
| `ByteArray.indexOf(sub, fromIndex)` | First occurrence of a byte sequence, or -1; never throws. |
| `ByteArray.readDoubleBe(offset)`, `ByteArray.readDoubleLe(offset)` | Read a 64-bit IEEE 754 double, exact bit pattern preserved. |
| `ByteArray.readFloatBe(offset)`, `ByteArray.readFloatLe(offset)` | Read a 32-bit IEEE 754 float, exact bit pattern preserved. |
| `ByteArray.readIntBe(offset)`, `ByteArray.readIntLe(offset)` | Read a 32-bit signed integer. |
| `ByteArray.readLongBe(offset)`, `ByteArray.readLongLe(offset)` | Read a 64-bit signed integer. |
| `ByteArray.readShortBe(offset)`, `ByteArray.readShortLe(offset)` | Read a 16-bit signed integer. |
| `ByteArray.startsWith(prefix)` | True when the array begins with the given bytes; never throws. |
| `ByteArray.toBase64()` | Standard base64, always padded. |
| `ByteArray.toBase64Url()` | Url-safe base64, never padded. |
| `Short.toByteArrayBe()`, `Int.toByteArrayBe()`, `Long.toByteArrayBe()`, `Float.toByteArrayBe()`, `Double.toByteArrayBe()` | New exact-size array, big-endian. |
| `Short.toByteArrayLe()`, `Int.toByteArrayLe()`, `Long.toByteArrayLe()`, `Float.toByteArrayLe()`, `Double.toByteArrayLe()` | New exact-size array, little-endian. |
| `ByteArray.toHex(upperCase)` | Two hex digits per byte; lowercase by default. |
| `ByteArray.writeDoubleBe(offset, value)`, `ByteArray.writeDoubleLe(offset, value)` | Write a 64-bit IEEE 754 double in place. |
| `ByteArray.writeFloatBe(offset, value)`, `ByteArray.writeFloatLe(offset, value)` | Write a 32-bit IEEE 754 float in place. |
| `ByteArray.writeIntBe(offset, value)`, `ByteArray.writeIntLe(offset, value)` | Write a 32-bit signed integer in place. |
| `ByteArray.writeLongBe(offset, value)`, `ByteArray.writeLongLe(offset, value)` | Write a 64-bit signed integer in place. |
| `ByteArray.writeShortBe(offset, value)`, `ByteArray.writeShortLe(offset, value)` | Write a 16-bit signed integer in place. |
| `ByteArray.xor(key)` | New array xor-combined with a repeating key; empty key throws. |
