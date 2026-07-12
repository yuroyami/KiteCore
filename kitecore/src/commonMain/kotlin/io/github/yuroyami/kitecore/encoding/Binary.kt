/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

private fun ByteArray.checkRange(offset: Int, count: Int) {
    if (offset < 0 || offset > size - count) {
        throw IndexOutOfBoundsException(
            "Range [$offset, ${offset + count}) is out of bounds for array of size $size"
        )
    }
}

// region Byte sequence utilities

/**
 * Returns a new array where every byte is xor-combined with the repeating key.
 *
 * The key is applied cyclically: output index `i` is `this[i] xor key[i % key.size]`.
 * The receiver is not modified. Applying the same key twice restores the
 * original bytes. An empty receiver produces an empty array.
 *
 * @param key the key bytes to repeat over the input.
 * @return a new array of the same size as the receiver.
 * @throws IllegalArgumentException if [key] is empty.
 */
public fun ByteArray.xor(key: ByteArray): ByteArray {
    require(key.isNotEmpty()) { "xor key must not be empty" }
    val out = ByteArray(size)
    for (i in indices) {
        out[i] = (this[i].toInt() xor key[i % key.size].toInt()).toByte()
    }
    return out
}

/**
 * Returns the index of the first occurrence of the given byte sequence at or after [fromIndex].
 *
 * A negative [fromIndex] is treated as 0. When [sub] is empty, the result is
 * [fromIndex] clamped into the range 0..size, matching the convention of
 * `String.indexOf` with an empty argument. This function never throws.
 *
 * @param sub the byte sequence to search for.
 * @param fromIndex the first index to consider, 0 by default.
 * @return the index of the first match, or -1 if [sub] does not occur at or
 *   after [fromIndex].
 */
public fun ByteArray.indexOf(sub: ByteArray, fromIndex: Int = 0): Int {
    val start = if (fromIndex < 0) 0 else fromIndex
    if (sub.isEmpty()) return if (start > size) size else start
    val limit = size - sub.size
    for (i in start..limit) {
        var j = 0
        while (j < sub.size && this[i + j] == sub[j]) j++
        if (j == sub.size) return i
    }
    return -1
}

/**
 * Returns whether this array begins with the given prefix.
 *
 * An empty prefix always matches, including against an empty array. A prefix
 * longer than the array never matches. This function never throws.
 *
 * @param prefix the byte sequence expected at index 0.
 * @return `true` if the first `prefix.size` bytes equal [prefix].
 */
public fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (prefix.size > size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

/**
 * Returns whether this array ends with the given suffix.
 *
 * An empty suffix always matches, including against an empty array. A suffix
 * longer than the array never matches. This function never throws.
 *
 * @param suffix the byte sequence expected at the end of the array.
 * @return `true` if the last `suffix.size` bytes equal [suffix].
 */
public fun ByteArray.endsWith(suffix: ByteArray): Boolean {
    if (suffix.size > size) return false
    val base = size - suffix.size
    for (i in suffix.indices) {
        if (this[base + i] != suffix[i]) return false
    }
    return true
}

// endregion

// region Reads

/**
 * Reads a 16-bit signed integer in little-endian order starting at [offset].
 *
 * Little-endian means the least significant byte is stored at the lowest
 * index, so the byte at [offset] contributes bits 0..7 and the byte at
 * `offset + 1` contributes bits 8..15.
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 2`
 *   exceeds the array size.
 */
public fun ByteArray.readShortLe(offset: Int): Short {
    checkRange(offset, 2)
    val b0 = this[offset].toInt() and 0xFF
    val b1 = this[offset + 1].toInt() and 0xFF
    return ((b1 shl 8) or b0).toShort()
}

/**
 * Reads a 16-bit signed integer in big-endian order starting at [offset].
 *
 * Big-endian means the most significant byte is stored at the lowest index,
 * so the byte at [offset] contributes bits 8..15 and the byte at `offset + 1`
 * contributes bits 0..7.
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 2`
 *   exceeds the array size.
 */
public fun ByteArray.readShortBe(offset: Int): Short {
    checkRange(offset, 2)
    val b0 = this[offset].toInt() and 0xFF
    val b1 = this[offset + 1].toInt() and 0xFF
    return ((b0 shl 8) or b1).toShort()
}

/**
 * Reads a 32-bit signed integer in little-endian order starting at [offset].
 *
 * Little-endian means the least significant byte is stored at the lowest
 * index: the byte at [offset] contributes bits 0..7 and the byte at
 * `offset + 3` contributes bits 24..31.
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.readIntLe(offset: Int): Int {
    checkRange(offset, 4)
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

/**
 * Reads a 32-bit signed integer in big-endian order starting at [offset].
 *
 * Big-endian means the most significant byte is stored at the lowest index:
 * the byte at [offset] contributes bits 24..31 and the byte at `offset + 3`
 * contributes bits 0..7.
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.readIntBe(offset: Int): Int {
    checkRange(offset, 4)
    return ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)
}

/**
 * Reads a 64-bit signed integer in little-endian order starting at [offset].
 *
 * Little-endian means the least significant byte is stored at the lowest
 * index: the byte at [offset] contributes bits 0..7 and the byte at
 * `offset + 7` contributes bits 56..63.
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.readLongLe(offset: Int): Long {
    checkRange(offset, 8)
    var result = 0L
    for (i in 7 downTo 0) {
        result = (result shl 8) or (this[offset + i].toLong() and 0xFF)
    }
    return result
}

/**
 * Reads a 64-bit signed integer in big-endian order starting at [offset].
 *
 * Big-endian means the most significant byte is stored at the lowest index:
 * the byte at [offset] contributes bits 56..63 and the byte at `offset + 7`
 * contributes bits 0..7.
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.readLongBe(offset: Int): Long {
    checkRange(offset, 8)
    var result = 0L
    for (i in 0 until 8) {
        result = (result shl 8) or (this[offset + i].toLong() and 0xFF)
    }
    return result
}

/**
 * Reads a 32-bit IEEE 754 float in little-endian order starting at [offset].
 *
 * The four bytes are decoded as with [readIntLe] and reinterpreted through
 * `Float.fromBits`, so the exact bit pattern is preserved, including NaN
 * payloads written by [writeFloatLe].
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.readFloatLe(offset: Int): Float = Float.fromBits(readIntLe(offset))

/**
 * Reads a 32-bit IEEE 754 float in big-endian order starting at [offset].
 *
 * The four bytes are decoded as with [readIntBe] and reinterpreted through
 * `Float.fromBits`, so the exact bit pattern is preserved, including NaN
 * payloads written by [writeFloatBe].
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.readFloatBe(offset: Int): Float = Float.fromBits(readIntBe(offset))

/**
 * Reads a 64-bit IEEE 754 double in little-endian order starting at [offset].
 *
 * The eight bytes are decoded as with [readLongLe] and reinterpreted through
 * `Double.fromBits`, so the exact bit pattern is preserved, including NaN
 * payloads written by [writeDoubleLe].
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.readDoubleLe(offset: Int): Double = Double.fromBits(readLongLe(offset))

/**
 * Reads a 64-bit IEEE 754 double in big-endian order starting at [offset].
 *
 * The eight bytes are decoded as with [readLongBe] and reinterpreted through
 * `Double.fromBits`, so the exact bit pattern is preserved, including NaN
 * payloads written by [writeDoubleBe].
 *
 * @param offset the index of the first byte to read.
 * @return the decoded value.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.readDoubleBe(offset: Int): Double = Double.fromBits(readLongBe(offset))

// endregion

// region Writes

/**
 * Writes a 16-bit signed integer in little-endian order starting at [offset].
 *
 * The least significant byte of [value] is stored at [offset] and the most
 * significant byte at `offset + 1`. The array is modified in place and no
 * other indices are touched.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 2`
 *   exceeds the array size.
 */
public fun ByteArray.writeShortLe(offset: Int, value: Short) {
    checkRange(offset, 2)
    val v = value.toInt()
    this[offset] = (v and 0xFF).toByte()
    this[offset + 1] = ((v ushr 8) and 0xFF).toByte()
}

/**
 * Writes a 16-bit signed integer in big-endian order starting at [offset].
 *
 * The most significant byte of [value] is stored at [offset] and the least
 * significant byte at `offset + 1`. The array is modified in place and no
 * other indices are touched.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 2`
 *   exceeds the array size.
 */
public fun ByteArray.writeShortBe(offset: Int, value: Short) {
    checkRange(offset, 2)
    val v = value.toInt()
    this[offset] = ((v ushr 8) and 0xFF).toByte()
    this[offset + 1] = (v and 0xFF).toByte()
}

/**
 * Writes a 32-bit signed integer in little-endian order starting at [offset].
 *
 * The least significant byte of [value] is stored at [offset] and the most
 * significant byte at `offset + 3`. The array is modified in place and no
 * other indices are touched.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.writeIntLe(offset: Int, value: Int) {
    checkRange(offset, 4)
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}

/**
 * Writes a 32-bit signed integer in big-endian order starting at [offset].
 *
 * The most significant byte of [value] is stored at [offset] and the least
 * significant byte at `offset + 3`. The array is modified in place and no
 * other indices are touched.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.writeIntBe(offset: Int, value: Int) {
    checkRange(offset, 4)
    this[offset] = ((value ushr 24) and 0xFF).toByte()
    this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 3] = (value and 0xFF).toByte()
}

/**
 * Writes a 64-bit signed integer in little-endian order starting at [offset].
 *
 * The least significant byte of [value] is stored at [offset] and the most
 * significant byte at `offset + 7`. The array is modified in place and no
 * other indices are touched.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.writeLongLe(offset: Int, value: Long) {
    checkRange(offset, 8)
    var v = value
    for (i in 0 until 8) {
        this[offset + i] = (v and 0xFF).toByte()
        v = v ushr 8
    }
}

/**
 * Writes a 64-bit signed integer in big-endian order starting at [offset].
 *
 * The most significant byte of [value] is stored at [offset] and the least
 * significant byte at `offset + 7`. The array is modified in place and no
 * other indices are touched.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.writeLongBe(offset: Int, value: Long) {
    checkRange(offset, 8)
    var v = value
    for (i in 7 downTo 0) {
        this[offset + i] = (v and 0xFF).toByte()
        v = v ushr 8
    }
}

/**
 * Writes a 32-bit IEEE 754 float in little-endian order starting at [offset].
 *
 * The value is converted with `Float.toRawBits`, which preserves the exact bit
 * pattern including NaN payloads, and stored as with [writeIntLe]. The array
 * is modified in place.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.writeFloatLe(offset: Int, value: Float) {
    writeIntLe(offset, value.toRawBits())
}

/**
 * Writes a 32-bit IEEE 754 float in big-endian order starting at [offset].
 *
 * The value is converted with `Float.toRawBits`, which preserves the exact bit
 * pattern including NaN payloads, and stored as with [writeIntBe]. The array
 * is modified in place.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 4`
 *   exceeds the array size.
 */
public fun ByteArray.writeFloatBe(offset: Int, value: Float) {
    writeIntBe(offset, value.toRawBits())
}

/**
 * Writes a 64-bit IEEE 754 double in little-endian order starting at [offset].
 *
 * The value is converted with `Double.toRawBits`, which preserves the exact
 * bit pattern including NaN payloads, and stored as with [writeLongLe]. The
 * array is modified in place.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.writeDoubleLe(offset: Int, value: Double) {
    writeLongLe(offset, value.toRawBits())
}

/**
 * Writes a 64-bit IEEE 754 double in big-endian order starting at [offset].
 *
 * The value is converted with `Double.toRawBits`, which preserves the exact
 * bit pattern including NaN payloads, and stored as with [writeLongBe]. The
 * array is modified in place.
 *
 * @param offset the index of the first byte to write.
 * @param value the value to encode.
 * @throws IndexOutOfBoundsException if [offset] is negative or `offset + 8`
 *   exceeds the array size.
 */
public fun ByteArray.writeDoubleBe(offset: Int, value: Double) {
    writeLongBe(offset, value.toRawBits())
}

// endregion

// region Conversions

/**
 * Returns this 16-bit value as a new 2-byte array in little-endian order.
 *
 * The least significant byte is at index 0. Equivalent to writing the value
 * with [writeShortLe] at offset 0. This function never throws.
 *
 * @return a new array of exactly 2 bytes.
 */
public fun Short.toByteArrayLe(): ByteArray = ByteArray(2).also { it.writeShortLe(0, this) }

/**
 * Returns this 16-bit value as a new 2-byte array in big-endian order.
 *
 * The most significant byte is at index 0. Equivalent to writing the value
 * with [writeShortBe] at offset 0. This function never throws.
 *
 * @return a new array of exactly 2 bytes.
 */
public fun Short.toByteArrayBe(): ByteArray = ByteArray(2).also { it.writeShortBe(0, this) }

/**
 * Returns this 32-bit value as a new 4-byte array in little-endian order.
 *
 * The least significant byte is at index 0. Equivalent to writing the value
 * with [writeIntLe] at offset 0. This function never throws.
 *
 * @return a new array of exactly 4 bytes.
 */
public fun Int.toByteArrayLe(): ByteArray = ByteArray(4).also { it.writeIntLe(0, this) }

/**
 * Returns this 32-bit value as a new 4-byte array in big-endian order.
 *
 * The most significant byte is at index 0. Equivalent to writing the value
 * with [writeIntBe] at offset 0. This function never throws.
 *
 * @return a new array of exactly 4 bytes.
 */
public fun Int.toByteArrayBe(): ByteArray = ByteArray(4).also { it.writeIntBe(0, this) }

/**
 * Returns this 64-bit value as a new 8-byte array in little-endian order.
 *
 * The least significant byte is at index 0. Equivalent to writing the value
 * with [writeLongLe] at offset 0. This function never throws.
 *
 * @return a new array of exactly 8 bytes.
 */
public fun Long.toByteArrayLe(): ByteArray = ByteArray(8).also { it.writeLongLe(0, this) }

/**
 * Returns this 64-bit value as a new 8-byte array in big-endian order.
 *
 * The most significant byte is at index 0. Equivalent to writing the value
 * with [writeLongBe] at offset 0. This function never throws.
 *
 * @return a new array of exactly 8 bytes.
 */
public fun Long.toByteArrayBe(): ByteArray = ByteArray(8).also { it.writeLongBe(0, this) }

/**
 * Returns this float as a new 4-byte array in little-endian order.
 *
 * The bit pattern from `Float.toRawBits` is stored with the least significant
 * byte at index 0, matching [writeFloatLe] at offset 0. This function never
 * throws.
 *
 * @return a new array of exactly 4 bytes.
 */
public fun Float.toByteArrayLe(): ByteArray = ByteArray(4).also { it.writeFloatLe(0, this) }

/**
 * Returns this float as a new 4-byte array in big-endian order.
 *
 * The bit pattern from `Float.toRawBits` is stored with the most significant
 * byte at index 0, matching [writeFloatBe] at offset 0. This function never
 * throws.
 *
 * @return a new array of exactly 4 bytes.
 */
public fun Float.toByteArrayBe(): ByteArray = ByteArray(4).also { it.writeFloatBe(0, this) }

/**
 * Returns this double as a new 8-byte array in little-endian order.
 *
 * The bit pattern from `Double.toRawBits` is stored with the least significant
 * byte at index 0, matching [writeDoubleLe] at offset 0. This function never
 * throws.
 *
 * @return a new array of exactly 8 bytes.
 */
public fun Double.toByteArrayLe(): ByteArray = ByteArray(8).also { it.writeDoubleLe(0, this) }

/**
 * Returns this double as a new 8-byte array in big-endian order.
 *
 * The bit pattern from `Double.toRawBits` is stored with the most significant
 * byte at index 0, matching [writeDoubleBe] at offset 0. This function never
 * throws.
 *
 * @return a new array of exactly 8 bytes.
 */
public fun Double.toByteArrayBe(): ByteArray = ByteArray(8).also { it.writeDoubleBe(0, this) }

// endregion
