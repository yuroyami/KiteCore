/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text

/**
 * Splits the receiver into word tokens at underscores, hyphens, whitespace,
 * and camel case transitions. Returns an empty list when no word characters
 * are present.
 */
private fun String.tokenizeWords(): List<String> {
    if (isEmpty()) return emptyList()
    val tokens = ArrayList<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
            current.setLength(0)
        }
    }
    for (i in indices) {
        val c = this[i]
        if (c == '_' || c == '-' || c.isWhitespace()) {
            flush()
            continue
        }
        if (c.isUpperCase() && current.isNotEmpty()) {
            val prev = this[i - 1]
            val nextIsLower = i + 1 < length && this[i + 1].isLowerCase()
            if (prev.isLowerCase() || prev.isDigit() || (prev.isUpperCase() && nextIsLower)) {
                flush()
            }
        }
        current.append(c)
    }
    flush()
    return tokens
}

/** Upper cases the first character of the token and lower cases the rest. */
private fun String.capitalizeToken(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1).lowercase()

/**
 * Returns a copy of this string with its first character converted to upper case.
 *
 * Returns this string unchanged when it is empty or when the first character
 * has no distinct upper case form.
 */
public fun String.capitalizeFirst(): String {
    if (isEmpty()) return this
    val first = this[0]
    val upper = first.uppercaseChar()
    return if (first == upper) this else upper + substring(1)
}

/**
 * Returns a copy of this string with its first character converted to lower case.
 *
 * Returns this string unchanged when it is empty or when the first character
 * has no distinct lower case form.
 */
public fun String.decapitalizeFirst(): String {
    if (isEmpty()) return this
    val first = this[0]
    val lower = first.lowercaseChar()
    return if (first == lower) this else lower + substring(1)
}

/**
 * Returns a copy of this string with the first character of every whitespace
 * separated word converted to upper case.
 *
 * All other characters are left unchanged. Returns this string unchanged when
 * it is empty.
 */
public fun String.capitalizeWords(): String {
    if (isEmpty()) return this
    val sb = StringBuilder(length)
    var atWordStart = true
    for (c in this) {
        if (c.isWhitespace()) {
            atWordStart = true
            sb.append(c)
        } else {
            sb.append(if (atWordStart) c.uppercaseChar() else c)
            atWordStart = false
        }
    }
    return sb.toString()
}

/**
 * Converts this string to camelCase.
 *
 * Words are detected at underscores, hyphens, whitespace, and camel case
 * transitions, so snake_case, kebab-case, space separated, and PascalCase
 * inputs are all accepted. The first word is lower cased entirely; every
 * following word starts with an upper case character followed by lower case.
 * Returns an empty string when no word characters are present.
 */
public fun String.toCamelCase(): String {
    val words = tokenizeWords()
    if (words.isEmpty()) return ""
    val sb = StringBuilder(length)
    sb.append(words[0].lowercase())
    for (k in 1 until words.size) sb.append(words[k].capitalizeToken())
    return sb.toString()
}

/**
 * Converts this string to PascalCase.
 *
 * Words are detected at underscores, hyphens, whitespace, and camel case
 * transitions. Every word starts with an upper case character followed by
 * lower case. Returns an empty string when no word characters are present.
 */
public fun String.toPascalCase(): String {
    val words = tokenizeWords()
    if (words.isEmpty()) return ""
    val sb = StringBuilder(length)
    for (word in words) sb.append(word.capitalizeToken())
    return sb.toString()
}

/**
 * Converts this string to snake_case.
 *
 * Words are detected at underscores, hyphens, whitespace, and camel case
 * transitions, then lower cased and joined with underscores. Returns an empty
 * string when no word characters are present.
 */
public fun String.toSnakeCase(): String =
    tokenizeWords().joinToString("_") { it.lowercase() }

/**
 * Converts this string to kebab-case.
 *
 * Words are detected at underscores, hyphens, whitespace, and camel case
 * transitions, then lower cased and joined with hyphens. Returns an empty
 * string when no word characters are present.
 */
public fun String.toKebabCase(): String =
    tokenizeWords().joinToString("-") { it.lowercase() }

/**
 * Converts this string to a lowercase ASCII slug.
 *
 * ASCII letters and digits are kept and lower cased. Runs of whitespace,
 * hyphens, and underscores between kept characters become a single hyphen.
 * Every other character is dropped. The result never starts or ends with a
 * hyphen. Returns an empty string when no ASCII letters or digits are present.
 */
public fun String.toSlug(): String {
    val sb = StringBuilder(length)
    var pendingSeparator = false
    for (c in this) {
        val lower = c.lowercaseChar()
        if (lower in 'a'..'z' || lower in '0'..'9') {
            if (pendingSeparator && sb.isNotEmpty()) sb.append('-')
            pendingSeparator = false
            sb.append(lower)
        } else if (c.isWhitespace() || c == '-' || c == '_') {
            pendingSeparator = true
        }
    }
    return sb.toString()
}

/**
 * Splits this string into its camel case segments.
 *
 * A boundary is placed before an upper case character that follows a lower
 * case character or digit, and before the final upper case character of an
 * acronym run when a lower case character follows it. Characters other than
 * letters and digits stay inside their segment. Returns an empty list for an
 * empty string and a single element list when no boundary exists.
 */
public fun String.splitCamelCase(): List<String> {
    if (isEmpty()) return emptyList()
    val parts = ArrayList<String>()
    var start = 0
    for (i in 1 until length) {
        val c = this[i]
        if (!c.isUpperCase()) continue
        val prev = this[i - 1]
        val nextIsLower = i + 1 < length && this[i + 1].isLowerCase()
        if (prev.isLowerCase() || prev.isDigit() || (prev.isUpperCase() && nextIsLower)) {
            parts.add(substring(start, i))
            start = i
        }
    }
    parts.add(substring(start))
    return parts
}

/**
 * Returns the upper cased first characters of up to [maxCount] whitespace
 * separated words.
 *
 * Returns an empty string when this string is blank or [maxCount] is zero or
 * negative.
 */
public fun String.initials(maxCount: Int = 2): String {
    if (maxCount <= 0) return ""
    val sb = StringBuilder(maxCount)
    var atWordStart = true
    for (c in this) {
        if (c.isWhitespace()) {
            atWordStart = true
            continue
        }
        if (atWordStart) {
            sb.append(c.uppercaseChar())
            if (sb.length == maxCount) return sb.toString()
            atWordStart = false
        }
    }
    return sb.toString()
}
