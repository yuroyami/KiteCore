/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text

/**
 * Returns the number of lines in this string, treating \n, \r\n, and \r as
 * line breaks.
 *
 * An empty string counts as one line; the result equals the number of line
 * breaks plus one.
 */
public fun String.lineCount(): Int {
    var count = 1
    var i = 0
    while (i < length) {
        when (this[i]) {
            '\n' -> count++
            '\r' -> {
                count++
                if (i + 1 < length && this[i + 1] == '\n') i++
            }
        }
        i++
    }
    return count
}

/**
 * Counts the non-overlapping occurrences of [substring] in this string.
 *
 * Returns 0 when [substring] is empty or does not occur.
 */
public fun String.countOccurrences(substring: String): Int {
    if (substring.isEmpty()) return 0
    var count = 0
    var index = indexOf(substring)
    while (index >= 0) {
        count++
        index = indexOf(substring, index + substring.length)
    }
    return count
}

/**
 * Returns the index of the nth non-overlapping occurrence of [substring],
 * where [n] counts from 1, or -1 when there are fewer than [n] occurrences.
 *
 * Returns -1 when [substring] is empty or [n] is less than 1.
 */
public fun String.indexOfNth(substring: String, n: Int): Int {
    if (n <= 0 || substring.isEmpty()) return -1
    var remaining = n
    var index = indexOf(substring)
    while (index >= 0 && --remaining > 0) {
        index = indexOf(substring, index + substring.length)
    }
    return index
}

/**
 * Returns true when this string is non-empty and every character is an ASCII
 * digit.
 *
 * Signs, decimal separators, and whitespace are not accepted.
 */
public fun String.isNumeric(): Boolean = isNotEmpty() && all { it in '0'..'9' }

/**
 * Returns true when this string is non-empty and every character is a letter.
 */
public fun String.isAlpha(): Boolean = isNotEmpty() && all { it.isLetter() }

/**
 * Returns true when this string is non-empty and every character is a letter
 * or a digit.
 */
public fun String.isAlphanumeric(): Boolean =
    isNotEmpty() && all { it.isLetter() || it.isDigit() }

/**
 * Returns true when this string is non-empty and every character is a
 * hexadecimal digit (0-9, a-f, or A-F).
 *
 * Prefixes such as "0x" are not accepted.
 */
public fun String.isHex(): Boolean =
    isNotEmpty() && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/**
 * Parses this string as a decimal [Int], returning [default] when it is not a
 * valid integer.
 */
public fun String.toIntOrDefault(default: Int): Int = toIntOrNull() ?: default

/**
 * Parses this string as a decimal [Long], returning [default] when it is not a
 * valid long integer.
 */
public fun String.toLongOrDefault(default: Long): Long = toLongOrNull() ?: default

/**
 * Parses this string as a [Float], returning [default] when it is not a valid
 * floating point number.
 */
public fun String.toFloatOrDefault(default: Float): Float = toFloatOrNull() ?: default

/**
 * Parses this string as a [Double], returning [default] when it is not a valid
 * floating point number.
 */
public fun String.toDoubleOrDefault(default: Double): Double = toDoubleOrNull() ?: default

/**
 * Parses this string as a boolean, returning [default] when it matches neither
 * value.
 *
 * The strings "true" and "false" are matched ignoring case; every other input
 * yields [default].
 */
public fun String.toBooleanOrDefault(default: Boolean): Boolean = when {
    equals("true", ignoreCase = true) -> true
    equals("false", ignoreCase = true) -> false
    else -> default
}

/**
 * Returns null when this string is blank, otherwise returns it unchanged.
 */
public fun String.nullIfBlank(): String? = if (isBlank()) null else this

/**
 * Returns null when this string is empty, otherwise returns it unchanged.
 */
public fun String.nullIfEmpty(): String? = if (isEmpty()) null else this

/**
 * Returns true when this string contains at least one element of [substrings].
 *
 * Returns false when [substrings] is empty. An empty candidate string is
 * contained in every string.
 */
public fun String.containsAny(vararg substrings: String, ignoreCase: Boolean = false): Boolean =
    substrings.any { contains(it, ignoreCase) }

/**
 * Returns true when this string contains every element of [substrings].
 *
 * Returns true when [substrings] is empty.
 */
public fun String.containsAll(vararg substrings: String, ignoreCase: Boolean = false): Boolean =
    substrings.all { contains(it, ignoreCase) }

/**
 * Returns true when this string equals at least one element of [candidates],
 * ignoring case.
 *
 * Returns false when [candidates] is empty.
 */
public fun String.equalsAnyIgnoreCase(vararg candidates: String): Boolean =
    candidates.any { equals(it, ignoreCase = true) }

/**
 * Returns true when this character is one of the ASCII vowels a, e, i, o, or
 * u, in either case.
 *
 * The letter y and accented vowels are not counted.
 */
public fun Char.isVowel(): Boolean = lowercaseChar() in "aeiou"

/**
 * Returns the number of bytes this string occupies when encoded as UTF-8,
 * computed from its code points without allocating an encoded copy.
 *
 * Surrogate pairs count as 4 bytes. An unpaired surrogate counts as 3 bytes,
 * the encoded size of the replacement character that standard encoders
 * substitute for it. Returns 0 for an empty string.
 */
public fun String.utf8Size(): Int {
    var size = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c.code < 0x80 -> size += 1
            c.code < 0x800 -> size += 2
            c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate() -> {
                size += 4
                i++
            }
            else -> size += 3
        }
        i++
    }
    return size
}
