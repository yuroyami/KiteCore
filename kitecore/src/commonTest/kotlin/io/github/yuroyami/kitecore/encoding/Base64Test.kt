/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64Test {

    @Test
    fun toBase64EncodesManAsTWFu() {
        assertEquals("TWFu", "Man".encodeToByteArray().toBase64())
    }

    @Test
    fun toBase64MatchesRfc4648Vectors() {
        assertEquals("", "".encodeToByteArray().toBase64())
        assertEquals("Zg==", "f".encodeToByteArray().toBase64())
        assertEquals("Zm8=", "fo".encodeToByteArray().toBase64())
        assertEquals("Zm9v", "foo".encodeToByteArray().toBase64())
        assertEquals("Zm9vYg==", "foob".encodeToByteArray().toBase64())
        assertEquals("Zm9vYmE=", "fooba".encodeToByteArray().toBase64())
        assertEquals("Zm9vYmFy", "foobar".encodeToByteArray().toBase64())
    }

    @Test
    fun decodeBase64MatchesRfc4648Vectors() {
        assertContentEquals(ByteArray(0), "".decodeBase64())
        assertContentEquals("f".encodeToByteArray(), "Zg==".decodeBase64())
        assertContentEquals("fo".encodeToByteArray(), "Zm8=".decodeBase64())
        assertContentEquals("foo".encodeToByteArray(), "Zm9v".decodeBase64())
        assertContentEquals("foob".encodeToByteArray(), "Zm9vYg==".decodeBase64())
        assertContentEquals("fooba".encodeToByteArray(), "Zm9vYmE=".decodeBase64())
        assertContentEquals("foobar".encodeToByteArray(), "Zm9vYmFy".decodeBase64())
    }

    @Test
    fun decodeBase64AcceptsUnpaddedInput() {
        assertContentEquals("f".encodeToByteArray(), "Zg".decodeBase64())
        assertContentEquals("fo".encodeToByteArray(), "Zm8".decodeBase64())
        assertContentEquals("Man".encodeToByteArray(), "TWFu".decodeBase64())
    }

    @Test
    fun base64RoundTripCoversAllByteValues() {
        val all = ByteArray(256) { it.toByte() }
        assertContentEquals(all, all.toBase64().decodeBase64())
    }

    @Test
    fun decodeBase64ThrowsOnInvalidCharacters() {
        assertFailsWith<IllegalArgumentException> { "TW#u".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "TW-u".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "TW_u".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "TWéu".decodeBase64() }
    }

    @Test
    fun decodeBase64ThrowsOnMalformedPadding() {
        assertFailsWith<IllegalArgumentException> { "TQ=".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "TWFu=".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "====".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "Zg==Zg==".decodeBase64() }
    }

    @Test
    fun decodeBase64ThrowsOnImpossibleLength() {
        assertFailsWith<IllegalArgumentException> { "A".decodeBase64() }
        assertFailsWith<IllegalArgumentException> { "Zm9vA".decodeBase64() }
    }

    @Test
    fun toBase64UrlUsesUrlAlphabetWithoutPadding() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xEF.toByte())
        assertEquals("++8=", bytes.toBase64())
        assertEquals("--8", bytes.toBase64Url())
        val highBytes = byteArrayOf(0xFF.toByte(), 0xE0.toByte())
        assertEquals("/+A=", highBytes.toBase64())
        assertEquals("_-A", highBytes.toBase64Url())
        assertEquals("", ByteArray(0).toBase64Url())
    }

    @Test
    fun decodeBase64UrlDecodesUrlAlphabet() {
        assertContentEquals(byteArrayOf(0xFB.toByte(), 0xEF.toByte()), "--8".decodeBase64Url())
        assertContentEquals(byteArrayOf(0xFB.toByte(), 0xEF.toByte()), "--8=".decodeBase64Url())
        assertContentEquals("Man".encodeToByteArray(), "TWFu".decodeBase64Url())
        assertContentEquals(ByteArray(0), "".decodeBase64Url())
    }

    @Test
    fun base64UrlRoundTripCoversAllByteValues() {
        val all = ByteArray(256) { it.toByte() }
        assertContentEquals(all, all.toBase64Url().decodeBase64Url())
    }

    @Test
    fun decodeBase64UrlThrowsOnStandardAlphabetCharacters() {
        assertFailsWith<IllegalArgumentException> { "++8".decodeBase64Url() }
        assertFailsWith<IllegalArgumentException> { "Zm9v/w".decodeBase64Url() }
    }

    @Test
    fun decodeBase64UrlThrowsOnMalformedInput() {
        assertFailsWith<IllegalArgumentException> { "A".decodeBase64Url() }
        assertFailsWith<IllegalArgumentException> { "--8==".decodeBase64Url() }
    }
}
