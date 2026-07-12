/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

private const val STD_ALPHABET: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private const val URL_ALPHABET: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private val STD_INVERSE: IntArray = buildInverse(STD_ALPHABET)
private val URL_INVERSE: IntArray = buildInverse(URL_ALPHABET)

private fun buildInverse(alphabet: String): IntArray {
    val table = IntArray(128) { -1 }
    for (i in alphabet.indices) table[alphabet[i].code] = i
    return table
}

/**
 * Encodes this byte array using the standard base64 alphabet of RFC 4648 section 4.
 *
 * The output uses the alphabet `A-Z a-z 0-9 + /` and is always padded with `=`
 * characters to a multiple of four characters. An empty array produces an empty
 * string. This function never throws.
 *
 * @return the padded base64 representation of this array.
 */
public fun ByteArray.toBase64(): String = encodeBase64(this, STD_ALPHABET, pad = true)

/**
 * Decodes this string using the standard base64 alphabet of RFC 4648 section 4.
 *
 * Both padded and unpadded input is accepted. When padding is present, the total
 * length must be a multiple of four and at most two `=` characters may appear,
 * only at the end. An empty string produces an empty array. Trailing bits that
 * do not form a full byte are discarded without validation, so non-canonical
 * final characters are accepted.
 *
 * @return the decoded bytes.
 * @throws IllegalArgumentException if the input contains a character outside the
 *   standard alphabet, if padding is malformed, or if the number of data
 *   characters leaves a remainder of one when divided by four.
 */
public fun String.decodeBase64(): ByteArray = decodeBase64(this, STD_INVERSE)

/**
 * Encodes this byte array using the url-safe base64 alphabet of RFC 4648 section 5.
 *
 * The output uses the alphabet `A-Z a-z 0-9 - _` and carries no `=` padding, which
 * makes it safe for URLs and file names. An empty array produces an empty string.
 * This function never throws.
 *
 * @return the unpadded url-safe base64 representation of this array.
 */
public fun ByteArray.toBase64Url(): String = encodeBase64(this, URL_ALPHABET, pad = false)

/**
 * Decodes this string using the url-safe base64 alphabet of RFC 4648 section 5.
 *
 * Both padded and unpadded input is accepted. When padding is present, the total
 * length must be a multiple of four and at most two `=` characters may appear,
 * only at the end. The standard characters `+` and `/` are rejected. An empty
 * string produces an empty array.
 *
 * @return the decoded bytes.
 * @throws IllegalArgumentException if the input contains a character outside the
 *   url-safe alphabet, if padding is malformed, or if the number of data
 *   characters leaves a remainder of one when divided by four.
 */
public fun String.decodeBase64Url(): ByteArray = decodeBase64(this, URL_INVERSE)

private fun encodeBase64(bytes: ByteArray, alphabet: String, pad: Boolean): String {
    val out = StringBuilder(((bytes.size + 2) / 3) * 4)
    var i = 0
    while (i + 3 <= bytes.size) {
        val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or
            ((bytes[i + 1].toInt() and 0xFF) shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        out.append(alphabet[(chunk ushr 18) and 0x3F])
        out.append(alphabet[(chunk ushr 12) and 0x3F])
        out.append(alphabet[(chunk ushr 6) and 0x3F])
        out.append(alphabet[chunk and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val chunk = (bytes[i].toInt() and 0xFF) shl 16
            out.append(alphabet[(chunk ushr 18) and 0x3F])
            out.append(alphabet[(chunk ushr 12) and 0x3F])
            if (pad) out.append("==")
        }
        2 -> {
            val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8)
            out.append(alphabet[(chunk ushr 18) and 0x3F])
            out.append(alphabet[(chunk ushr 12) and 0x3F])
            out.append(alphabet[(chunk ushr 6) and 0x3F])
            if (pad) out.append('=')
        }
    }
    return out.toString()
}

private fun decodeBase64(input: String, inverse: IntArray): ByteArray {
    var end = input.length
    var padding = 0
    while (end > 0 && input[end - 1] == '=') {
        end--
        padding++
    }
    if (padding > 0 && (padding > 2 || input.length % 4 != 0)) {
        throw IllegalArgumentException("Malformed base64 padding in input of length ${input.length}")
    }
    val remainder = end % 4
    if (remainder == 1) {
        throw IllegalArgumentException("Invalid base64 input: $end data characters is not a valid length")
    }
    val outSize = (end / 4) * 3 + when (remainder) {
        2 -> 1
        3 -> 2
        else -> 0
    }
    val out = ByteArray(outSize)
    var outPos = 0
    var buffer = 0
    var bits = 0
    for (i in 0 until end) {
        val c = input[i]
        val v = if (c.code < 128) inverse[c.code] else -1
        if (v < 0) throw IllegalArgumentException("Invalid base64 character '$c' at index $i")
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out[outPos++] = ((buffer ushr bits) and 0xFF).toByte()
        }
    }
    return out
}
