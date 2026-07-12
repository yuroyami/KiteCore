/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.math

/**
 * Returns `true` when every bit of [flag] is set in this mask.
 *
 * A multi-bit [flag] requires all of its bits to be present. A [flag] of zero
 * always returns `true` because the empty bit set is contained in every mask.
 */
public fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag

/**
 * Returns this mask with every bit of [flag] set.
 *
 * Bits that are already set stay set; the operation is idempotent.
 */
public fun Int.withFlag(flag: Int): Int = this or flag

/**
 * Returns this mask with every bit of [flag] cleared.
 *
 * Bits that are already clear stay clear; the operation is idempotent.
 */
public fun Int.withoutFlag(flag: Int): Int = this and flag.inv()

/**
 * Returns this mask with every bit of [flag] inverted.
 *
 * Applying the same [flag] twice restores the original mask.
 */
public fun Int.toggleFlag(flag: Int): Int = this xor flag

/**
 * This value in kilobytes (multiples of 1000) expressed as bytes.
 *
 * The multiplication runs in [Long] and cannot overflow for any [Int] receiver.
 */
public val Int.KB: Long get() = this * 1_000L

/**
 * This value in megabytes (multiples of 1000^2) expressed as bytes.
 *
 * The multiplication runs in [Long] and cannot overflow for any [Int] receiver.
 */
public val Int.MB: Long get() = this * 1_000_000L

/**
 * This value in gigabytes (multiples of 1000^3) expressed as bytes.
 *
 * The multiplication runs in [Long] and cannot overflow for any [Int] receiver.
 */
public val Int.GB: Long get() = this * 1_000_000_000L

/**
 * This value in kibibytes (multiples of 1024) expressed as bytes.
 *
 * The multiplication runs in [Long] and cannot overflow for any [Int] receiver.
 */
public val Int.KiB: Long get() = this * 1_024L

/**
 * This value in mebibytes (multiples of 1024^2) expressed as bytes.
 *
 * The multiplication runs in [Long] and cannot overflow for any [Int] receiver.
 */
public val Int.MiB: Long get() = this * 1_048_576L

/**
 * This value in gibibytes (multiples of 1024^3) expressed as bytes.
 *
 * The multiplication runs in [Long] and cannot overflow for any [Int] receiver.
 */
public val Int.GiB: Long get() = this * 1_073_741_824L
