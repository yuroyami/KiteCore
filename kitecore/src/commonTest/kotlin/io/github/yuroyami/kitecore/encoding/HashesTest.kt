/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashesTest {

    @Test
    fun crc32MatchesCheckVector() {
        assertEquals(0xCBF43926L, crc32("123456789".encodeToByteArray()))
    }

    @Test
    fun crc32OfEmptyInputIsZero() {
        assertEquals(0L, crc32(ByteArray(0)))
        assertEquals(0L, "".crc32())
    }

    @Test
    fun crc32MatchesKnownSingleByteVector() {
        assertEquals(0xE8B7BE43L, crc32("a".encodeToByteArray()))
    }

    @Test
    fun crc32StringExtensionUsesUtf8() {
        assertEquals(0xCBF43926L, "123456789".crc32())
        assertEquals(crc32("héllo".encodeToByteArray()), "héllo".crc32())
    }

    @Test
    fun crc32IsAlwaysNonNegative() {
        val inputs = listOf("", "a", "abc", "123456789", "ÿþ", "some longer input text")
        for (s in inputs) {
            val value = s.crc32()
            assertTrue(value >= 0L, "crc32 of \"$s\" was negative: $value")
            assertTrue(value <= 0xFFFFFFFFL, "crc32 of \"$s\" exceeded 32 bits: $value")
        }
        assertTrue(crc32(byteArrayOf(-1, -2, -3)) >= 0L)
    }

    @Test
    fun fnv1a32OfEmptyInputIsOffsetBasis() {
        assertEquals(0x811C9DC5L.toInt(), fnv1a32(ByteArray(0)))
        assertEquals(0x811C9DC5L.toInt(), "".fnv1a32())
    }

    @Test
    fun fnv1a32MatchesKnownVectors() {
        assertEquals(0xE40C292CL.toInt(), fnv1a32("a".encodeToByteArray()))
        assertEquals(0xBF9CF968L.toInt(), fnv1a32("foobar".encodeToByteArray()))
    }

    @Test
    fun fnv1a32StringExtensionUsesUtf8() {
        assertEquals(0xE40C292CL.toInt(), "a".fnv1a32())
        assertEquals(fnv1a32("héllo".encodeToByteArray()), "héllo".fnv1a32())
    }

    @Test
    fun fnv1a64OfEmptyInputIsOffsetBasis() {
        // 0xCBF29CE484222325 as a signed Long.
        assertEquals(-0x340D631B7BDDDCDBL, fnv1a64(ByteArray(0)))
        assertEquals(-0x340D631B7BDDDCDBL, "".fnv1a64())
    }

    @Test
    fun fnv1a64MatchesKnownVectors() {
        // 0xAF63DC4C8601EC8C as a signed Long.
        assertEquals(-0x509C23B379FE1374L, fnv1a64("a".encodeToByteArray()))
        // 0x85944171F73967E8 as a signed Long.
        assertEquals(-0x7A6BBE8E08C69818L, fnv1a64("foobar".encodeToByteArray()))
    }

    @Test
    fun fnv1a64StringExtensionUsesUtf8() {
        assertEquals(-0x509C23B379FE1374L, "a".fnv1a64())
        assertEquals(fnv1a64("héllo".encodeToByteArray()), "héllo".fnv1a64())
    }

    @Test
    fun hashesAreDeterministic() {
        val bytes = ByteArray(64) { (it * 7).toByte() }
        assertEquals(crc32(bytes), crc32(bytes))
        assertEquals(fnv1a32(bytes), fnv1a32(bytes))
        assertEquals(fnv1a64(bytes), fnv1a64(bytes))
    }
}
