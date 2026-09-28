/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text.consumer

import io.github.yuroyami.kitecore.text.StringFormatException
import io.github.yuroyami.kitecore.text.format as kiteFormat
import java.util.IllegalFormatException
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compares the portable formatter with java.util.Formatter on seeded random inputs.
 *
 * The seeds are fixed, so a failure always reproduces.
 */
class StringFormatFuzzJvmTest {

    @Test
    fun typeMatchedSpecifiersMatchJava() {
        val random = Random(20260928)
        val mismatches = mutableListOf<String>()
        repeat(40_000) {
            val (format, argument) = typeMatchedCase(random)
            compare(format, arrayOf(argument), mismatches)
        }
        assertTrue(mismatches.isEmpty(), report(mismatches))
    }

    @Test
    fun arbitrarySpecifiersFailOrSucceedLikeJava() {
        val random = Random(9281)
        val mismatches = mutableListOf<String>()
        repeat(20_000) {
            val format = buildString {
                repeat(1 + random.nextInt(2)) {
                    if (random.nextInt(3) == 0) append('x')
                    append(randomSpecifier(random))
                }
            }
            val arguments = Array(1 + random.nextInt(3)) { randomArgument(random) }
            compare(format, arguments, mismatches)
        }
        assertTrue(mismatches.isEmpty(), report(mismatches))
    }

    private fun compare(format: String, arguments: Array<Any?>, mismatches: MutableList<String>) {
        val java = runCatching { java.lang.String.format(Locale.ROOT, format, *arguments) }
        val kite = runCatching { format.kiteFormat(*arguments) }
        val javaFailure = java.exceptionOrNull()
        val kiteFailure = kite.exceptionOrNull()
        val problem = when {
            java.isSuccess && kite.isSuccess ->
                if (java.getOrThrow() == kite.getOrThrow()) null else "java=\"${java.getOrThrow()}\" kite=\"${kite.getOrThrow()}\""
            java.isSuccess -> "java=\"${java.getOrThrow()}\" kite threw $kiteFailure"
            kite.isSuccess -> "java threw $javaFailure kite=\"${kite.getOrThrow()}\""
            javaFailure !is IllegalFormatException -> "java threw an unexpected $javaFailure"
            kiteFailure !is StringFormatException -> "kite threw $kiteFailure instead of StringFormatException"
            else -> null
        }
        if (problem != null) {
            mismatches += "format=\"$format\" arguments=${arguments.map { describe(it) }} $problem"
        }
    }

    private fun report(mismatches: List<String>): String =
        "${mismatches.size} mismatches with java.util.Formatter, first ones:\n" +
            mismatches.take(20).joinToString("\n")

    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is Char -> "Char(U+${value.code.toString(16)})"
        else -> "${value::class.simpleName}($value)"
    }

    private fun typeMatchedCase(random: Random): Pair<String, Any?> {
        val conversion = "dddoxXxeEffffgGaAssSbcC"[random.nextInt(23)]
        val allowedFlags = when (conversion.lowercaseChar()) {
            'd' -> "-+ 0,("
            'o', 'x' -> "-#0"
            'e' -> "-#+ 0("
            'f' -> "-#+ 0,("
            'g' -> "-+ 0,("
            'a' -> "-#+ 0"
            else -> "-"
        }
        val flags = buildSet { repeat(random.nextInt(3)) { add(allowedFlags[random.nextInt(allowedFlags.length)]) } }
        val format = buildString {
            append('%')
            flags.forEach { append(it) }
            if ('-' in flags || '0' in flags || random.nextBoolean()) append(1 + random.nextInt(30))
            if (random.nextBoolean() && conversion !in "dxXoc") append('.').append(random.nextInt(20))
            append(conversion)
        }
        val argument: Any? = when (conversion.lowercaseChar()) {
            'd', 'o', 'x' -> when (random.nextInt(5)) {
                0 -> random.nextInt()
                1 -> random.nextLong()
                2 -> random.nextInt().toShort()
                3 -> random.nextInt().toByte()
                else -> longArrayOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)[random.nextInt(4)]
            }
            'e', 'f', 'g', 'a' -> randomFloating(random)
            'c' -> if (random.nextBoolean()) (32 + random.nextInt(0x2F00)).toChar() else random.nextInt(0x10FFFF)
            else -> randomArgument(random)
        }
        return format to argument
    }

    private fun randomSpecifier(random: Random): String = buildString {
        append('%')
        when (random.nextInt(10)) {
            0, 1 -> append(1 + random.nextInt(3)).append('$')
            2 -> append('<')
        }
        if (random.nextInt(4) == 0) repeat(1 + random.nextInt(3)) { append("-#+ 0,("[random.nextInt(7)]) }
        if (random.nextBoolean()) append(1 + random.nextInt(24))
        if (random.nextInt(3) == 0) append('.').append(random.nextInt(21))
        append("bBhHsScCdoxXeEfgGaA%n"[random.nextInt(21)])
    }

    private fun randomArgument(random: Random): Any? = when (random.nextInt(11)) {
        0 -> random.nextInt()
        1 -> random.nextLong()
        2 -> random.nextInt().toShort()
        3 -> random.nextInt().toByte()
        4, 5 -> randomFloating(random)
        6 -> (random.nextInt(0x3000)).toChar()
        7 -> listOf("", "a", "hello", "Straße", "İstanbul", "😀x", "abc def")[random.nextInt(7)]
        8 -> random.nextBoolean()
        9 -> null
        else -> random.nextInt(0x110002) - 1
    }

    private fun randomFloating(random: Random): Any = when (random.nextInt(6)) {
        0 -> doubleArrayOf(
            0.0, -0.0, 0.1, 0.5, 1.5, 2.5, 1.005, 2.675, 0.125, 1e-5, 9.9995, 1e21, 1e-300,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE, Double.MAX_VALUE, 4.35, 0.045, 99.95,
        ).let { it[random.nextInt(it.size)] }
        1 -> Double.fromBits(random.nextLong())
        2 -> Float.fromBits(random.nextInt())
        3 -> floatArrayOf(0f, -0f, 0.1f, 1.5f, Float.MIN_VALUE, Float.MAX_VALUE, 16777217f, 1e-10f)
            .let { it[random.nextInt(it.size)] }
        else -> (random.nextDouble() - 0.5) * 10.0.pow(random.nextInt(40) - 20)
    }
}
