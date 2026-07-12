/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text

/**
 * Shortens this string to at most [maxLength] characters, replacing the
 * removed tail with [ellipsis].
 *
 * Returns this string unchanged when it already fits. When [maxLength] is
 * smaller than the length of [ellipsis], the ellipsis itself is cut to fit.
 * Returns an empty string when [maxLength] is zero or negative.
 */
public fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (maxLength <= 0) return ""
    if (length <= maxLength) return this
    if (ellipsis.length >= maxLength) return ellipsis.take(maxLength)
    return take(maxLength - ellipsis.length) + ellipsis
}

/**
 * Shortens this string to at most [maxLength] characters by removing a middle
 * section and inserting [ellipsis] in its place.
 *
 * The start and end of the string are kept; when the kept character count is
 * odd, the extra character comes from the start. Returns this string unchanged
 * when it already fits. When [maxLength] is smaller than the length of
 * [ellipsis], the ellipsis itself is cut to fit. Returns an empty string when
 * [maxLength] is zero or negative.
 */
public fun String.truncateMiddle(maxLength: Int, ellipsis: String = "..."): String {
    if (maxLength <= 0) return ""
    if (length <= maxLength) return this
    if (ellipsis.length >= maxLength) return ellipsis.take(maxLength)
    val keep = maxLength - ellipsis.length
    val front = (keep + 1) / 2
    return take(front) + ellipsis + takeLast(keep - front)
}

/**
 * Replaces every run of whitespace in this string with a single space and
 * removes leading and trailing whitespace.
 *
 * Returns an empty string when this string is blank.
 */
public fun String.collapseWhitespace(): String {
    val sb = StringBuilder(length)
    var pendingSpace = false
    for (c in this) {
        if (c.isWhitespace()) {
            pendingSpace = sb.isNotEmpty()
        } else {
            if (pendingSpace) {
                sb.append(' ')
                pendingSpace = false
            }
            sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * Returns a copy of this string with every whitespace character removed.
 */
public fun String.removeWhitespace(): String = filterNot { it.isWhitespace() }

/**
 * Prepends [spaces] space characters to every line of this string, including
 * blank lines.
 *
 * Line breaks (\n, \r\n, and \r) are recognized and preserved as they are, and
 * no padding is added after a trailing line break. Returns this string
 * unchanged when it is empty or [spaces] is zero or negative.
 */
public fun String.indent(spaces: Int): String {
    if (spaces <= 0 || isEmpty()) return this
    val pad = " ".repeat(spaces)
    val sb = StringBuilder(length + pad.length)
    sb.append(pad)
    for (i in indices) {
        val c = this[i]
        sb.append(c)
        val isBreak = c == '\n' || (c == '\r' && (i + 1 >= length || this[i + 1] != '\n'))
        if (isBreak && i + 1 < length) sb.append(pad)
    }
    return sb.toString()
}

/**
 * Returns this string with [prefix] prepended unless it already starts with
 * [prefix].
 *
 * The comparison is case sensitive. An empty [prefix] leaves the string
 * unchanged.
 */
public fun String.prependIfMissing(prefix: String): String =
    if (startsWith(prefix)) this else prefix + this

/**
 * Returns this string with [suffix] appended unless it already ends with
 * [suffix].
 *
 * The comparison is case sensitive. An empty [suffix] leaves the string
 * unchanged.
 */
public fun String.appendIfMissing(suffix: String): String =
    if (endsWith(suffix)) this else this + suffix

/**
 * Removes [prefix] from the start of this string, comparing case insensitively.
 *
 * Returns this string unchanged when it does not start with [prefix] or when
 * [prefix] is empty.
 */
public fun String.removePrefixIgnoreCase(prefix: String): String =
    if (prefix.isNotEmpty() && startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

/**
 * Removes [suffix] from the end of this string, comparing case insensitively.
 *
 * Returns this string unchanged when it does not end with [suffix] or when
 * [suffix] is empty.
 */
public fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (suffix.isNotEmpty() && endsWith(suffix, ignoreCase = true)) substring(0, length - suffix.length) else this

/**
 * Returns the substring after the first occurrence of [start] and before the
 * next occurrence of [end], or null when either delimiter is missing.
 *
 * An empty [start] matches at the beginning of the string, and an empty [end]
 * matches immediately after [start], producing an empty result.
 */
public fun String.substringBetween(start: String, end: String): String? {
    val from = indexOf(start)
    if (from < 0) return null
    val innerStart = from + start.length
    val to = indexOf(end, innerStart)
    if (to < 0) return null
    return substring(innerStart, to)
}

/**
 * Splits this string at the first occurrence of [delimiter] into the part
 * before it and the part after it, or returns null when [delimiter] is absent.
 *
 * The delimiter itself is not included in either component. An empty delimiter
 * matches at position zero and yields an empty first component.
 */
public fun String.splitToPair(delimiter: String): Pair<String, String>? {
    val at = indexOf(delimiter)
    if (at < 0) return null
    return substring(0, at) to substring(at + delimiter.length)
}

/**
 * Returns the content of this string before its first line break, or the whole
 * string when it contains no line break.
 *
 * Returns an empty string for an empty receiver.
 */
public fun String.firstLine(): String {
    for (i in indices) {
        val c = this[i]
        if (c == '\n' || c == '\r') return substring(0, i)
    }
    return this
}

/**
 * Returns the content of this string after its last line break, or the whole
 * string when it contains no line break.
 *
 * A string ending with a line break yields an empty last line.
 */
public fun String.lastLine(): String {
    for (i in length - 1 downTo 0) {
        val c = this[i]
        if (c == '\n' || c == '\r') return substring(i + 1)
    }
    return this
}

/**
 * Pads this string on both sides with [padChar] so that the result is [length]
 * characters long, keeping the original content centered.
 *
 * When the padding cannot be split evenly, the extra character goes to the
 * right side. Returns this string unchanged when [length] is not greater than
 * its current length.
 */
public fun String.padCenter(length: Int, padChar: Char = ' '): String {
    if (length <= this.length) return this
    val total = length - this.length
    val left = total / 2
    val sb = StringBuilder(length)
    repeat(left) { sb.append(padChar) }
    sb.append(this)
    repeat(total - left) { sb.append(padChar) }
    return sb.toString()
}

/**
 * Replaces every character of this string with [maskChar] except the first
 * [visibleStart] and the last [visibleEnd] characters.
 *
 * Negative arguments count as zero. When the visible ranges cover the whole
 * string, it is returned unchanged.
 */
public fun String.mask(visibleStart: Int = 0, visibleEnd: Int = 0, maskChar: Char = '*'): String {
    val startKeep = visibleStart.coerceIn(0, length)
    val endKeep = visibleEnd.coerceIn(0, length - startKeep)
    val maskedCount = length - startKeep - endKeep
    if (maskedCount <= 0) return this
    return substring(0, startKeep) + "".padEnd(maskedCount, maskChar) + substring(length - endKeep)
}

/**
 * Word wraps this string so that no line exceeds [maxLineLength] characters
 * where possible.
 *
 * Existing line breaks are kept as breaks and normalized to \n. Words within a
 * line are joined by single spaces in the output, so runs of spaces and tabs
 * collapse. A word longer than [maxLineLength] is placed on a line of its own
 * without being broken. Returns this string unchanged when [maxLineLength] is
 * zero or negative.
 */
public fun String.wrap(maxLineLength: Int): String {
    if (maxLineLength <= 0) return this
    return lines().joinToString("\n") { line ->
        val words = line.split(' ', '\t').filter { it.isNotEmpty() }
        val sb = StringBuilder(line.length)
        var currentLength = 0
        for (word in words) {
            when {
                currentLength == 0 -> {
                    sb.append(word)
                    currentLength = word.length
                }
                currentLength + 1 + word.length <= maxLineLength -> {
                    sb.append(' ').append(word)
                    currentLength += 1 + word.length
                }
                else -> {
                    sb.append('\n').append(word)
                    currentLength = word.length
                }
            }
        }
        sb.toString()
    }
}

/**
 * Returns a copy of this string containing only its digit characters.
 *
 * Returns an empty string when no digits are present.
 */
public fun String.digitsOnly(): String = filter { it.isDigit() }

/**
 * Returns a copy of this string containing only its letter characters.
 *
 * Returns an empty string when no letters are present.
 */
public fun String.lettersOnly(): String = filter { it.isLetter() }

/** Returns the first [n] lines joined with a line feed. Fewer lines return the whole string. */
public fun String.takeLines(n: Int): String {
    require(n >= 0) { "n must not be negative, was $n" }
    return lineSequence().take(n).joinToString("\n")
}

/** Returns the string without its first [n] lines. Dropping every line returns an empty string. */
public fun String.dropLines(n: Int): String {
    require(n >= 0) { "n must not be negative, was $n" }
    return lineSequence().drop(n).joinToString("\n")
}
