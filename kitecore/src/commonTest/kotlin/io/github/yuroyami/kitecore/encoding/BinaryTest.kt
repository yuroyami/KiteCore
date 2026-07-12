/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinaryTest {

    private val sample = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)

    // region xor

    @Test
    fun xorWithSingleByteKey() {
        val data = byteArrayOf(0x00, 0xFF.toByte(), 0x0F)
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0x00, 0xF0.toByte()),
            data.xor(byteArrayOf(0xFF.toByte())),
        )
    }

    @Test
    fun xorRepeatsKeyCyclically() {
        val data = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)
        val key = byteArrayOf(0x01, 0x02)
        assertContentEquals(byteArrayOf(0x11, 0x22, 0x31, 0x42, 0x51), data.xor(key))
    }

    @Test
    fun xorTwiceRestoresOriginal() {
        val data = ByteArray(32) { (it * 11).toByte() }
        val key = byteArrayOf(0x5A, 0xC3.toByte(), 0x17)
        assertContentEquals(data, data.xor(key).xor(key))
    }

    @Test
    fun xorDoesNotModifyReceiver() {
        val data = byteArrayOf(1, 2, 3)
        data.xor(byteArrayOf(0x7F))
        assertContentEquals(byteArrayOf(1, 2, 3), data)
    }

    @Test
    fun xorOfEmptyArrayIsEmpty() {
        assertContentEquals(ByteArray(0), ByteArray(0).xor(byteArrayOf(1)))
    }

    @Test
    fun xorThrowsOnEmptyKey() {
        assertFailsWith<IllegalArgumentException> { byteArrayOf(1).xor(ByteArray(0)) }
    }

    // endregion

    // region indexOf, startsWith, endsWith

    @Test
    fun indexOfFindsSubsequence() {
        assertEquals(0, sample.indexOf(byteArrayOf(0x01, 0x02)))
        assertEquals(2, sample.indexOf(byteArrayOf(0x03, 0x04, 0x05)))
        assertEquals(7, sample.indexOf(byteArrayOf(0x08)))
    }

    @Test
    fun indexOfReturnsMinusOneWhenAbsent() {
        assertEquals(-1, sample.indexOf(byteArrayOf(0x09)))
        assertEquals(-1, sample.indexOf(byteArrayOf(0x02, 0x01)))
        assertEquals(-1, sample.indexOf(byteArrayOf(0x07, 0x08, 0x09)))
        assertEquals(-1, ByteArray(0).indexOf(byteArrayOf(0x01)))
    }

    @Test
    fun indexOfHonorsFromIndex() {
        val data = byteArrayOf(1, 2, 1, 2, 1, 2)
        assertEquals(0, data.indexOf(byteArrayOf(1, 2)))
        assertEquals(2, data.indexOf(byteArrayOf(1, 2), fromIndex = 1))
        assertEquals(4, data.indexOf(byteArrayOf(1, 2), fromIndex = 3))
        assertEquals(-1, data.indexOf(byteArrayOf(1, 2), fromIndex = 5))
    }

    @Test
    fun indexOfTreatsNegativeFromIndexAsZero() {
        assertEquals(0, sample.indexOf(byteArrayOf(0x01), fromIndex = -5))
    }

    @Test
    fun indexOfWithEmptySubReturnsClampedFromIndex() {
        assertEquals(0, sample.indexOf(ByteArray(0)))
        assertEquals(3, sample.indexOf(ByteArray(0), fromIndex = 3))
        assertEquals(sample.size, sample.indexOf(ByteArray(0), fromIndex = 100))
    }

    @Test
    fun indexOfFindsOverlappingCandidates() {
        val data = byteArrayOf(1, 1, 2)
        assertEquals(1, data.indexOf(byteArrayOf(1, 2)))
    }

    @Test
    fun startsWithMatchesPrefixes() {
        assertTrue(sample.startsWith(ByteArray(0)))
        assertTrue(sample.startsWith(byteArrayOf(0x01)))
        assertTrue(sample.startsWith(byteArrayOf(0x01, 0x02, 0x03)))
        assertTrue(sample.startsWith(sample))
        assertTrue(ByteArray(0).startsWith(ByteArray(0)))
    }

    @Test
    fun startsWithRejectsNonPrefixes() {
        assertFalse(sample.startsWith(byteArrayOf(0x02)))
        assertFalse(sample.startsWith(byteArrayOf(0x01, 0x03)))
        assertFalse(byteArrayOf(0x01).startsWith(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun endsWithMatchesSuffixes() {
        assertTrue(sample.endsWith(ByteArray(0)))
        assertTrue(sample.endsWith(byteArrayOf(0x08)))
        assertTrue(sample.endsWith(byteArrayOf(0x06, 0x07, 0x08)))
        assertTrue(sample.endsWith(sample))
        assertTrue(ByteArray(0).endsWith(ByteArray(0)))
    }

    @Test
    fun endsWithRejectsNonSuffixes() {
        assertFalse(sample.endsWith(byteArrayOf(0x07)))
        assertFalse(sample.endsWith(byteArrayOf(0x08, 0x07)))
        assertFalse(byteArrayOf(0x01).endsWith(byteArrayOf(0x01, 0x01)))
    }

    // endregion

    // region reads

    @Test
    fun readShortHonorsEndianness() {
        assertEquals(0x0201.toShort(), sample.readShortLe(0))
        assertEquals(0x0102.toShort(), sample.readShortBe(0))
        assertEquals(0x0302.toShort(), sample.readShortLe(1))
        assertEquals(0x0203.toShort(), sample.readShortBe(1))
    }

    @Test
    fun readShortHandlesNegativeValues() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x80.toByte(), 0x00)
        assertEquals((-1).toShort(), bytes.readShortLe(0))
        assertEquals((-1).toShort(), bytes.readShortBe(0))
        assertEquals(0x0080.toShort(), bytes.readShortLe(2))
        assertEquals((-32768).toShort(), bytes.readShortBe(2))
    }

    @Test
    fun readIntHonorsEndianness() {
        assertEquals(0x04030201, sample.readIntLe(0))
        assertEquals(0x01020304, sample.readIntBe(0))
        assertEquals(0x08070605, sample.readIntLe(4))
        assertEquals(0x05060708, sample.readIntBe(4))
    }

    @Test
    fun readIntHandlesNegativeValues() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(-1, bytes.readIntLe(0))
        assertEquals(-1, bytes.readIntBe(0))
    }

    @Test
    fun readLongHonorsEndianness() {
        assertEquals(0x0807060504030201L, sample.readLongLe(0))
        assertEquals(0x0102030405060708L, sample.readLongBe(0))
    }

    @Test
    fun readLongHandlesNegativeValues() {
        val bytes = ByteArray(8) { 0xFF.toByte() }
        assertEquals(-1L, bytes.readLongLe(0))
        assertEquals(-1L, bytes.readLongBe(0))
    }

    @Test
    fun readFloatDecodesKnownBitPattern() {
        // 1.0f has the IEEE 754 bit pattern 0x3F800000.
        val be = byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00)
        val le = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F)
        assertEquals(1.0f, be.readFloatBe(0))
        assertEquals(1.0f, le.readFloatLe(0))
    }

    @Test
    fun readDoubleDecodesKnownBitPattern() {
        // 1.0 has the IEEE 754 bit pattern 0x3FF0000000000000.
        val be = byteArrayOf(0x3F, 0xF0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val le = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F)
        assertEquals(1.0, be.readDoubleBe(0))
        assertEquals(1.0, le.readDoubleLe(0))
    }

    @Test
    fun readsThrowOnOutOfBounds() {
        assertFailsWith<IndexOutOfBoundsException> { sample.readShortLe(-1) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readShortBe(7) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readIntLe(5) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readIntBe(-1) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readLongLe(1) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readLongBe(9) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readFloatLe(6) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readFloatBe(-2) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readDoubleLe(1) }
        assertFailsWith<IndexOutOfBoundsException> { sample.readDoubleBe(-1) }
    }

    // endregion

    // region writes

    @Test
    fun writeShortProducesExpectedBytes() {
        val le = ByteArray(2)
        le.writeShortLe(0, 0x0102.toShort())
        assertContentEquals(byteArrayOf(0x02, 0x01), le)
        val be = ByteArray(2)
        be.writeShortBe(0, 0x0102.toShort())
        assertContentEquals(byteArrayOf(0x01, 0x02), be)
    }

    @Test
    fun writeIntProducesExpectedBytes() {
        val le = ByteArray(4)
        le.writeIntLe(0, 0x01020304)
        assertContentEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), le)
        val be = ByteArray(4)
        be.writeIntBe(0, 0x01020304)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), be)
    }

    @Test
    fun writeLongProducesExpectedBytes() {
        val le = ByteArray(8)
        le.writeLongLe(0, 0x0102030405060708L)
        assertContentEquals(byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01), le)
        val be = ByteArray(8)
        be.writeLongBe(0, 0x0102030405060708L)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08), be)
    }

    @Test
    fun writeFloatProducesExpectedBytes() {
        val be = ByteArray(4)
        be.writeFloatBe(0, 1.0f)
        assertContentEquals(byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00), be)
        val le = ByteArray(4)
        le.writeFloatLe(0, 1.0f)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), le)
    }

    @Test
    fun writeDoubleProducesExpectedBytes() {
        val be = ByteArray(8)
        be.writeDoubleBe(0, 1.0)
        assertContentEquals(byteArrayOf(0x3F, 0xF0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), be)
        val le = ByteArray(8)
        le.writeDoubleLe(0, 1.0)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F), le)
    }

    @Test
    fun writesOnlyTouchTheTargetRange() {
        val buffer = ByteArray(8) { 0x77 }
        buffer.writeShortBe(3, 0x0102.toShort())
        assertContentEquals(
            byteArrayOf(0x77, 0x77, 0x77, 0x01, 0x02, 0x77, 0x77, 0x77),
            buffer,
        )
    }

    @Test
    fun writeReadRoundTripsAtVariousOffsets() {
        val buffer = ByteArray(16)
        buffer.writeShortLe(1, (-12345).toShort())
        assertEquals((-12345).toShort(), buffer.readShortLe(1))
        buffer.writeShortBe(3, 31000.toShort())
        assertEquals(31000.toShort(), buffer.readShortBe(3))
        buffer.writeIntLe(5, -123456789)
        assertEquals(-123456789, buffer.readIntLe(5))
        buffer.writeIntBe(9, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, buffer.readIntBe(9))
        buffer.writeLongLe(8, Long.MIN_VALUE)
        assertEquals(Long.MIN_VALUE, buffer.readLongLe(8))
        buffer.writeLongBe(0, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, buffer.readLongBe(0))
        buffer.writeFloatLe(2, -3.5f)
        assertEquals(-3.5f, buffer.readFloatLe(2))
        buffer.writeFloatBe(6, Float.MIN_VALUE)
        // Kotlin/JS backs Float with a JS Number, so denormals compare by bits.
        assertEquals(Float.MIN_VALUE.toRawBits(), buffer.readFloatBe(6).toRawBits())
        buffer.writeDoubleLe(4, -2.5e300)
        assertEquals(-2.5e300, buffer.readDoubleLe(4))
        buffer.writeDoubleBe(8, Double.MAX_VALUE)
        assertEquals(Double.MAX_VALUE, buffer.readDoubleBe(8))
    }

    @Test
    fun floatAndDoubleRoundTripsPreserveSpecialValues() {
        val buffer = ByteArray(8)
        buffer.writeFloatLe(0, Float.POSITIVE_INFINITY)
        assertEquals(Float.POSITIVE_INFINITY, buffer.readFloatLe(0))
        buffer.writeFloatBe(0, Float.NaN)
        assertTrue(buffer.readFloatBe(0).isNaN())
        buffer.writeDoubleLe(0, Double.NEGATIVE_INFINITY)
        assertEquals(Double.NEGATIVE_INFINITY, buffer.readDoubleLe(0))
        buffer.writeDoubleBe(0, Double.NaN)
        assertTrue(buffer.readDoubleBe(0).isNaN())
    }

    @Test
    fun writesThrowOnOutOfBounds() {
        val buffer = ByteArray(8)
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeShortLe(-1, 1) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeShortBe(7, 1) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeIntLe(5, 1) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeIntBe(-1, 1) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeLongLe(1, 1L) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeLongBe(9, 1L) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeFloatLe(6, 1.0f) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeFloatBe(-2, 1.0f) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeDoubleLe(1, 1.0) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.writeDoubleBe(-1, 1.0) }
    }

    // endregion

    // region conversions

    @Test
    fun shortToByteArrayHonorsEndianness() {
        assertContentEquals(byteArrayOf(0x02, 0x01), 0x0102.toShort().toByteArrayLe())
        assertContentEquals(byteArrayOf(0x01, 0x02), 0x0102.toShort().toByteArrayBe())
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), (-1).toShort().toByteArrayLe())
    }

    @Test
    fun intToByteArrayHonorsEndianness() {
        assertContentEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), 0x01020304.toByteArrayLe())
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), 0x01020304.toByteArrayBe())
        assertContentEquals(ByteArray(4) { 0xFF.toByte() }, (-1).toByteArrayBe())
    }

    @Test
    fun longToByteArrayHonorsEndianness() {
        assertContentEquals(
            byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01),
            0x0102030405060708L.toByteArrayLe(),
        )
        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            0x0102030405060708L.toByteArrayBe(),
        )
        assertContentEquals(ByteArray(8) { 0xFF.toByte() }, (-1L).toByteArrayLe())
    }

    @Test
    fun floatToByteArrayHonorsEndianness() {
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), 1.0f.toByteArrayLe())
        assertContentEquals(byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00), 1.0f.toByteArrayBe())
        assertEquals(-7.25f, (-7.25f).toByteArrayLe().readFloatLe(0))
        assertEquals(-7.25f, (-7.25f).toByteArrayBe().readFloatBe(0))
    }

    @Test
    fun doubleToByteArrayHonorsEndianness() {
        assertContentEquals(
            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F),
            1.0.toByteArrayLe(),
        )
        assertContentEquals(
            byteArrayOf(0x3F, 0xF0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            1.0.toByteArrayBe(),
        )
        assertEquals(-1.5e-12, (-1.5e-12).toByteArrayLe().readDoubleLe(0))
        assertEquals(-1.5e-12, (-1.5e-12).toByteArrayBe().readDoubleBe(0))
    }

    @Test
    fun conversionsRoundTripThroughReads() {
        assertEquals(12345.toShort(), 12345.toShort().toByteArrayLe().readShortLe(0))
        assertEquals(12345.toShort(), 12345.toShort().toByteArrayBe().readShortBe(0))
        assertEquals(-987654321, (-987654321).toByteArrayLe().readIntLe(0))
        assertEquals(-987654321, (-987654321).toByteArrayBe().readIntBe(0))
        assertEquals(-98765432123456789L, (-98765432123456789L).toByteArrayLe().readLongLe(0))
        assertEquals(-98765432123456789L, (-98765432123456789L).toByteArrayBe().readLongBe(0))
    }

    // endregion
}
