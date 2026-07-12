/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.encoding

private val CRC32_TABLE: IntArray = run {
    val poly = 0xEDB88320.toInt()
    val table = IntArray(256)
    for (n in 0 until 256) {
        var c = n
        repeat(8) {
            c = if (c and 1 != 0) (c ushr 1) xor poly else c ushr 1
        }
        table[n] = c
    }
    table
}

/** FNV-1a 32-bit offset basis, 0x811C9DC5 as a signed Int. */
private const val FNV32_OFFSET: Int = -0x7EE3623B

/** FNV-1a 32-bit prime, 16777619. */
private const val FNV32_PRIME: Int = 0x01000193

/** FNV-1a 64-bit offset basis, 0xCBF29CE484222325 as a signed Long. */
private const val FNV64_OFFSET: Long = -0x340D631B7BDDDCDBL

/** FNV-1a 64-bit prime, 1099511628211. */
private const val FNV64_PRIME: Long = 0x100000001B3L

/**
 * Computes the CRC-32 checksum of the given bytes.
 *
 * This is the IEEE 802.3 variant (reflected polynomial 0xEDB88320, initial value
 * 0xFFFFFFFF, final xor 0xFFFFFFFF), the same algorithm used by zip and PNG.
 * The unsigned 32-bit result is returned in the low 32 bits of a Long, so the
 * value is always in the range 0..4294967295 and never negative. An empty
 * array hashes to 0. This function never throws.
 *
 * CRC-32 is a non-cryptographic checksum intended for error detection. It must
 * not be used for security purposes such as integrity against tampering.
 *
 * @param bytes the input to checksum.
 * @return the CRC-32 value as an unsigned quantity in a Long.
 */
public fun crc32(bytes: ByteArray): Long {
    var crc = -1
    for (b in bytes) {
        crc = (crc ushr 8) xor CRC32_TABLE[(crc xor b.toInt()) and 0xFF]
    }
    return crc.inv().toLong() and 0xFFFFFFFFL
}

/**
 * Computes the CRC-32 checksum of this string encoded as UTF-8.
 *
 * The string is first converted to its UTF-8 bytes and then hashed with
 * [crc32], so all documentation of that function applies. An empty string
 * hashes to 0. This function never throws.
 *
 * @return the CRC-32 value as an unsigned quantity in a Long.
 */
public fun String.crc32(): Long = crc32(encodeToByteArray())

/**
 * Computes the 32-bit FNV-1a hash of the given bytes.
 *
 * The algorithm starts from the offset basis 0x811C9DC5 and, for every byte,
 * applies xor first and then multiplication by the prime 16777619, with all
 * arithmetic wrapping modulo 2 to the power of 32. An empty array hashes to
 * the offset basis itself. The result is the raw 32-bit pattern in an Int and
 * may be negative. This function never throws.
 *
 * FNV-1a is a non-cryptographic hash intended for hash tables and fast
 * fingerprinting. It must not be used for security purposes.
 *
 * @param bytes the input to hash.
 * @return the 32-bit FNV-1a hash.
 */
public fun fnv1a32(bytes: ByteArray): Int {
    var hash = FNV32_OFFSET
    for (b in bytes) {
        hash = hash xor (b.toInt() and 0xFF)
        hash *= FNV32_PRIME
    }
    return hash
}

/**
 * Computes the 32-bit FNV-1a hash of this string encoded as UTF-8.
 *
 * The string is first converted to its UTF-8 bytes and then hashed with
 * [fnv1a32], so all documentation of that function applies. An empty string
 * hashes to the offset basis 0x811C9DC5. This function never throws.
 *
 * @return the 32-bit FNV-1a hash.
 */
public fun String.fnv1a32(): Int = fnv1a32(encodeToByteArray())

/**
 * Computes the 64-bit FNV-1a hash of the given bytes.
 *
 * The algorithm starts from the offset basis 0xCBF29CE484222325 and, for every
 * byte, applies xor first and then multiplication by the prime 1099511628211,
 * with all arithmetic wrapping modulo 2 to the power of 64. An empty array
 * hashes to the offset basis itself. The result is the raw 64-bit pattern in a
 * Long and may be negative. This function never throws.
 *
 * FNV-1a is a non-cryptographic hash intended for hash tables and fast
 * fingerprinting. It must not be used for security purposes.
 *
 * @param bytes the input to hash.
 * @return the 64-bit FNV-1a hash.
 */
public fun fnv1a64(bytes: ByteArray): Long {
    var hash = FNV64_OFFSET
    for (b in bytes) {
        hash = hash xor (b.toLong() and 0xFF)
        hash *= FNV64_PRIME
    }
    return hash
}

/**
 * Computes the 64-bit FNV-1a hash of this string encoded as UTF-8.
 *
 * The string is first converted to its UTF-8 bytes and then hashed with
 * [fnv1a64], so all documentation of that function applies. An empty string
 * hashes to the offset basis 0xCBF29CE484222325. This function never throws.
 *
 * @return the 64-bit FNV-1a hash.
 */
public fun String.fnv1a64(): Long = fnv1a64(encodeToByteArray())
