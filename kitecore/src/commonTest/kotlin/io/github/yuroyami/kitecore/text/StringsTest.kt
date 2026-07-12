/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringsTest {

    @Test
    fun capitalizationOfFirstCharacter() {
        assertEquals("Hello", "hello".capitalizeFirst())
        assertEquals("Hello", "Hello".capitalizeFirst())
        assertEquals("", "".capitalizeFirst())
        assertEquals("1abc", "1abc".capitalizeFirst())

        assertEquals("hello", "Hello".decapitalizeFirst())
        assertEquals("hello", "hello".decapitalizeFirst())
        assertEquals("", "".decapitalizeFirst())
    }

    @Test
    fun capitalizationOfWords() {
        assertEquals("Hello Brave World", "hello brave world".capitalizeWords())
        assertEquals("A  B", "a  b".capitalizeWords())
        assertEquals("HELLO", "hELLO".capitalizeWords())
        assertEquals("", "".capitalizeWords())
    }

    @Test
    fun caseConversions() {
        assertEquals("helloWorld", "hello_world".toCamelCase())
        assertEquals("helloWorld", "hello-world".toCamelCase())
        assertEquals("helloWorld", "Hello World".toCamelCase())
        assertEquals("xmlParser", "XMLParser".toCamelCase())
        assertEquals("", "".toCamelCase())
        assertEquals("", "___".toCamelCase())

        assertEquals("HelloWorld", "hello_world".toPascalCase())
        assertEquals("ApiKey", "api-key".toPascalCase())
        assertEquals("", "".toPascalCase())

        assertEquals("hello_world", "helloWorld".toSnakeCase())
        assertEquals("xml_parser", "XMLParser".toSnakeCase())
        assertEquals("hello_world_foo", "hello-world foo".toSnakeCase())
        assertEquals("", "".toSnakeCase())

        assertEquals("hello-world", "helloWorld".toKebabCase())
        assertEquals("hello-world", "HELLO_WORLD".toKebabCase())
        assertEquals("", "".toKebabCase())
    }

    @Test
    fun slugConversion() {
        assertEquals("hello-world", "Hello, World!".toSlug())
        assertEquals("kotlin-multiplatform-20", "Kotlin_Multiplatform 2.0".toSlug())
        assertEquals("abc", "  abc  ".toSlug())
        assertEquals("", "!!!".toSlug())
        assertEquals("", "".toSlug())
    }

    @Test
    fun camelCaseSplitting() {
        assertEquals(listOf("hello", "World", "Foo"), "helloWorldFoo".splitCamelCase())
        assertEquals(listOf("XML", "Parser"), "XMLParser".splitCamelCase())
        assertEquals(listOf("version2", "Beta"), "version2Beta".splitCamelCase())
        assertEquals(listOf("plain"), "plain".splitCamelCase())
        assertEquals(emptyList<String>(), "".splitCamelCase())
    }

    @Test
    fun initialsExtraction() {
        assertEquals("JS", "john smith".initials())
        assertEquals("JRT", "john ronald tolkien".initials(3))
        assertEquals("J", "  john   smith ".initials(1))
        assertEquals("", "   ".initials())
        assertEquals("", "john".initials(0))
    }

    @Test
    fun truncation() {
        assertEquals("hello...", "hello world".truncate(8))
        assertEquals("hello", "hello".truncate(8))
        assertEquals("..", "hello world".truncate(2))
        assertEquals("", "hello".truncate(0))
        assertEquals("hello wo~", "hello world!".truncate(9, "~"))

        assertEquals("ab...ij", "abcdefghij".truncateMiddle(7))
        assertEquals("abcdefghij", "abcdefghij".truncateMiddle(10))
        assertEquals("ab~yz", "abcdefghijklmnopqrstuvwxyz".truncateMiddle(5, "~"))
        assertEquals("", "abcdef".truncateMiddle(-1))
    }

    @Test
    fun whitespaceHandling() {
        assertEquals("a b c", "  a \t b \n c  ".collapseWhitespace())
        assertEquals("", "   ".collapseWhitespace())

        assertEquals("abc", " a b\tc\n".removeWhitespace())
        assertEquals("", "".removeWhitespace())
    }

    @Test
    fun indentation() {
        assertEquals("  a\n  b", "a\nb".indent(2))
        assertEquals("  a\r\n  b", "a\r\nb".indent(2))
        assertEquals("  a\n", "a\n".indent(2))
        assertEquals("a\nb", "a\nb".indent(0))
        assertEquals("", "".indent(2))
    }

    @Test
    fun prefixAndSuffix() {
        assertEquals("https://example.com", "example.com".prependIfMissing("https://"))
        assertEquals("https://example.com", "https://example.com".prependIfMissing("https://"))

        assertEquals("file.txt", "file".appendIfMissing(".txt"))
        assertEquals("file.txt", "file.txt".appendIfMissing(".txt"))

        assertEquals("example.com", "HTTPS://example.com".removePrefixIgnoreCase("https://"))
        assertEquals("example.com", "example.com".removePrefixIgnoreCase("https://"))

        assertEquals("file", "file.TXT".removeSuffixIgnoreCase(".txt"))
        assertEquals("file.md", "file.md".removeSuffixIgnoreCase(".txt"))
    }

    @Test
    fun substringExtraction() {
        assertEquals("x", "<a>x</a>".substringBetween("<a>", "</a>"))
        assertEquals("", "<a></a>".substringBetween("<a>", "</a>"))
        assertNull("<a>x".substringBetween("<a>", "</a>"))
        assertNull("x</a>".substringBetween("<a>", "</a>"))

        assertEquals("key" to "value", "key=value".splitToPair("="))
        assertEquals("a" to "b=c", "a=b=c".splitToPair("="))
        assertNull("abc".splitToPair("="))
    }

    @Test
    fun lineOperations() {
        assertEquals("first", "first\nsecond\nthird".firstLine())
        assertEquals("only", "only".firstLine())
        assertEquals("", "".firstLine())

        assertEquals("third", "first\nsecond\nthird".lastLine())
        assertEquals("", "first\n".lastLine())
        assertEquals("only", "only".lastLine())

        assertEquals(3, "a\r\nb\nc".lineCount())
        assertEquals(1, "".lineCount())
        assertEquals(2, "a\n".lineCount())
    }

    @Test
    fun paddingAndMasking() {
        assertEquals(" ab  ", "ab".padCenter(5))
        assertEquals("--abc--", "abc".padCenter(7, '-'))
        assertEquals("abc", "abc".padCenter(2))

        assertEquals("12******90", "1234567890".mask(2, 2))
        assertEquals("***", "abc".mask())
        assertEquals("ab", "ab".mask(2, 2))
        assertEquals("###", "abc".mask(-1, -1, '#'))
    }

    @Test
    fun countingAndSearching() {
        assertEquals(2, "aaaa".countOccurrences("aa"))
        assertEquals(3, "abcabcabc".countOccurrences("abc"))
        assertEquals(0, "abc".countOccurrences("x"))
        assertEquals(0, "abc".countOccurrences(""))

        assertEquals(0, "abcabcabc".indexOfNth("abc", 1))
        assertEquals(3, "abcabcabc".indexOfNth("abc", 2))
        assertEquals(-1, "abcabcabc".indexOfNth("abc", 4))
        assertEquals(-1, "abc".indexOfNth("abc", 0))
        assertEquals(-1, "abc".indexOfNth("", 1))
    }

    @Test
    fun characterClassPredicates() {
        assertTrue("12345".isNumeric())
        assertFalse("12.5".isNumeric())
        assertFalse("-1".isNumeric())
        assertFalse("".isNumeric())

        assertTrue("Hello".isAlpha())
        assertFalse("Hello1".isAlpha())
        assertFalse("".isAlpha())

        assertTrue("Hello123".isAlphanumeric())
        assertFalse("Hello 123".isAlphanumeric())
        assertFalse("".isAlphanumeric())

        assertTrue("DeadBeef01".isHex())
        assertFalse("0xFF".isHex())
        assertFalse("xyz".isHex())
        assertFalse("".isHex())
    }

    @Test
    fun numericConversions() {
        assertEquals(42, "42".toIntOrDefault(7))
        assertEquals(7, "forty-two".toIntOrDefault(7))

        assertEquals(9_000_000_000L, "9000000000".toLongOrDefault(0L))
        assertEquals(-1L, "".toLongOrDefault(-1L))

        assertEquals(2.5f, "2.5".toFloatOrDefault(0f))
        assertEquals(1.25f, "NaN?".toFloatOrDefault(1.25f))

        assertEquals(3.25, "3.25".toDoubleOrDefault(0.0))
        assertEquals(1.5, "oops".toDoubleOrDefault(1.5))

        assertTrue("TRUE".toBooleanOrDefault(false))
        assertFalse("False".toBooleanOrDefault(true))
        assertTrue("yes".toBooleanOrDefault(true))
    }

    @Test
    fun nullability() {
        assertNull("   ".nullIfBlank())
        assertEquals("a", "a".nullIfBlank())

        assertNull("".nullIfEmpty())
        assertEquals(" ", " ".nullIfEmpty())
    }

    @Test
    fun containment() {
        assertTrue("Hello World".containsAny("xyz", "World"))
        assertFalse("Hello World".containsAny("xyz", "world"))
        assertTrue("Hello World".containsAny("WORLD", ignoreCase = true))
        assertFalse("Hello World".containsAny())

        assertTrue("Hello World".containsAll("Hello", "World"))
        assertFalse("Hello World".containsAll("Hello", "world"))
        assertTrue("Hello World".containsAll("HELLO", "WORLD", ignoreCase = true))
        assertTrue("Hello World".containsAll())

        assertTrue("YES".equalsAnyIgnoreCase("no", "yes"))
        assertFalse("maybe".equalsAnyIgnoreCase("no", "yes"))
        assertFalse("maybe".equalsAnyIgnoreCase())
    }

    @Test
    fun wordWrapping() {
        assertEquals("the quick\nbrown fox", "the quick brown fox".wrap(10))
        assertEquals("short", "short".wrap(10))
        assertEquals("a\nsupercalifragilistic\nb", "a supercalifragilistic b".wrap(5))
        assertEquals("keep\nit", "keep\nit".wrap(10))
        assertEquals("no wrap", "no wrap".wrap(0))
    }

    @Test
    fun characterFilters() {
        assertEquals("0123456789", "(01) 234-567.89".digitsOnly())
        assertEquals("", "!@#".digitsOnly())

        assertEquals("abcDEF", "abc123DEF!".lettersOnly())
        assertEquals("", "123".lettersOnly())
    }

    @Test
    fun vowelPredicate() {
        assertTrue('a'.isVowel())
        assertTrue('E'.isVowel())
        assertFalse('y'.isVowel())
        assertFalse('b'.isVowel())
        assertFalse('1'.isVowel())
    }

    @Test
    fun utf8ByteSize() {
        assertEquals(0, "".utf8Size())
        assertEquals(5, "hello".utf8Size())
        assertEquals(2, "\u00E9".utf8Size())
        assertEquals(3, "\u20AC".utf8Size())
        assertEquals(4, "\uD834\uDD1E".utf8Size())
        assertEquals(10, "a\u00E9\u20AC\uD834\uDD1E".utf8Size())
        // Built at runtime: a lone-surrogate LITERAL does not survive the
        // Kotlin/JS compiler's UTF-8 emission of generated source.
        assertEquals(3, 0xD834.toChar().toString().utf8Size())
    }

    @Test
    fun takeAndDropLines() {
        val text = "a\nb\nc"
        assertEquals("a\nb", text.takeLines(2))
        assertEquals("c", text.dropLines(2))
        assertEquals(text, text.takeLines(10))
        assertEquals("", text.dropLines(10))
        assertFailsWith<IllegalArgumentException> { text.takeLines(-1) }
        assertFailsWith<IllegalArgumentException> { text.dropLines(-1) }
    }
}
