/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HexTest {

    @Test
    fun toHexEncodesKnownBytesLowerCase() {
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        assertEquals("007f80ff", bytes.toHex())
        assertEquals("007f80ff", bytes.toHex(upperCase = false))
    }

    @Test
    fun toHexEncodesKnownBytesUpperCase() {
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        assertEquals("007F80FF", bytes.toHex(upperCase = true))
    }

    @Test
    fun toHexOfEmptyArrayIsEmptyString() {
        assertEquals("", ByteArray(0).toHex())
        assertEquals("", ByteArray(0).toHex(upperCase = true))
    }

    @Test
    fun decodeHexDecodesKnownString() {
        assertContentEquals(
            byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()),
            "007f80ff".decodeHex(),
        )
    }

    @Test
    fun decodeHexAcceptsMixedCase() {
        assertContentEquals(
            byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()),
            "AbCdEf".decodeHex(),
        )
    }

    @Test
    fun decodeHexOfEmptyStringIsEmptyArray() {
        assertContentEquals(ByteArray(0), "".decodeHex())
    }

    @Test
    fun hexRoundTripCoversAllByteValues() {
        val all = ByteArray(256) { it.toByte() }
        assertContentEquals(all, all.toHex().decodeHex())
        assertContentEquals(all, all.toHex(upperCase = true).decodeHex())
    }

    @Test
    fun decodeHexThrowsOnOddLength() {
        assertFailsWith<IllegalArgumentException> { "abc".decodeHex() }
        assertFailsWith<IllegalArgumentException> { "0".decodeHex() }
    }

    @Test
    fun decodeHexThrowsOnInvalidCharacters() {
        assertFailsWith<IllegalArgumentException> { "zz".decodeHex() }
        assertFailsWith<IllegalArgumentException> { "0g".decodeHex() }
        assertFailsWith<IllegalArgumentException> { "1 ".decodeHex() }
    }
}
