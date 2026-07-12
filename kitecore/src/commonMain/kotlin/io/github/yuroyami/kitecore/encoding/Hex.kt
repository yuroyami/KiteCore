/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

private const val HEX_LOWER: String = "0123456789abcdef"
private const val HEX_UPPER: String = "0123456789ABCDEF"

/**
 * Encodes this byte array as a hexadecimal string.
 *
 * Each byte produces exactly two hexadecimal digits, most significant nibble first,
 * so the output length is always twice the input length. An empty array produces
 * an empty string. This function never throws.
 *
 * @param upperCase when `true`, emits digits `A` to `F` in upper case; when `false`
 *   (the default), emits `a` to `f` in lower case.
 * @return the hexadecimal representation of this array.
 */
public fun ByteArray.toHex(upperCase: Boolean = false): String {
    val digits = if (upperCase) HEX_UPPER else HEX_LOWER
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(digits[v ushr 4])
        out.append(digits[v and 0x0F])
    }
    return out.toString()
}

/**
 * Decodes this hexadecimal string into a byte array.
 *
 * Both lower case (`a` to `f`) and upper case (`A` to `F`) digits are accepted,
 * in any mix. Every pair of digits produces one byte, most significant nibble
 * first. An empty string produces an empty array.
 *
 * @return the decoded bytes, with length equal to half the string length.
 * @throws IllegalArgumentException if the string length is odd, or if any
 *   character is not a valid hexadecimal digit.
 */
public fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length, was $length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = hexDigitValue(this[2 * i], 2 * i)
        val lo = hexDigitValue(this[2 * i + 1], 2 * i + 1)
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private fun hexDigitValue(c: Char, index: Int): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw IllegalArgumentException("Invalid hex character '$c' at index $index")
}
