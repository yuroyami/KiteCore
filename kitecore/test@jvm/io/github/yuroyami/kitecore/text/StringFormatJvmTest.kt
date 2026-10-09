/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text.consumer

import io.github.yuroyami.kitecore.text.StringFormatException
import io.github.yuroyami.kitecore.text.format as kiteFormat
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringFormatJvmTest {

    @Test
    fun explicitlyImportedReceiverAndCompanionFormsResolveToKiteCore() {
        assertEquals("value=007", "value=%03d".kiteFormat(7))
        assertEquals("value=007", String.kiteFormat("value=%03d", 7))
    }

    @Test
    fun portableFormatsMatchJavaStringFormatWithRootLocale() {
        cases().forEach { case ->
            val expected = java.lang.String.format(Locale.ROOT, case.format, *case.arguments)
            val receiverResult = case.format.kiteFormat(*case.arguments)
            val companionResult = String.kiteFormat(case.format, *case.arguments)

            assertEquals(expected, receiverResult, "receiver form for ${case.format}")
            assertEquals(expected, companionResult, "companion form for ${case.format}")
        }
    }

    @Test
    fun jvmPreservesStrictNumericFamilyChecks() {
        assertFailsWith<StringFormatException> { "%f".kiteFormat(1) }
        assertFailsWith<StringFormatException> { "%d".kiteFormat(1.0) }
    }

    @Test
    fun edgeScientificFloatingPointFormatsMatchJavaRootFormatter() {
        assertFloatingMatrixMatchesRoot(
            "%e",
            "%E",
            "%.0e",
            "%#.0e",
            "%.1e",
            "%.6e",
            "%.17e",
            "%24.8e",
            "%-24.8E",
            "%+024.8e",
            "% 024.8e",
            "%(024.8e",
            "%#024.0E",
            "%+(024.4e",
        )
    }

    @Test
    fun edgeFixedFloatingPointFormatsMatchJavaRootFormatter() {
        assertFloatingMatrixMatchesRoot(
            "%f",
            "%.0f",
            "%#.0f",
            "%.1f",
            "%.2f",
            "%.6f",
            "%.17f",
            "%24.8f",
            "%-24.8f",
            "%+024.8f",
            "% 024.8f",
            "%(024.8f",
            "%,.3f",
            "%0,30.3f",
            "%(,030.3f",
            "%-,30.3f",
            "%#0,30.0f",
            "%+,(024.4f",
        )
        assertMatchesJavaRoot("%.326f", Double.MIN_VALUE)
    }

    @Test
    fun edgeGeneralFloatingPointFormatsMatchJavaRootFormatter() {
        assertFloatingMatrixMatchesRoot(
            "%g",
            "%G",
            "%.0g",
            "%.1g",
            "%.4g",
            "%.6g",
            "%.17g",
            "%24.8g",
            "%-24.8G",
            "%+024.8g",
            "% 024.8g",
            "%(024.8g",
            "%,.8g",
            "%0,30.8G",
            "%(,030.8g",
            "%-,30.8G",
            "%,.4g",
            "%+,(024.4g",
        )
    }

    @Test
    fun edgeHexadecimalFloatingPointFormatsMatchJavaRootFormatter() {
        assertFloatingMatrixMatchesRoot(
            "%a",
            "%A",
            "%#a",
            "%.0a",
            "%#.0a",
            "%.1a",
            "%.6a",
            "%.12a",
            "%.13a",
            "%.17a",
            "%24.8a",
            "%-24.8A",
            "%+024.8a",
            "% 024.8a",
            "%024.8A",
            "%#024.0A",
            "%+#024.3A",
        )
    }

    @Test
    fun hexadecimalPrecisionPaddingMatchesJavaWhenRenderedTextAlreadyMeetsWidth() {
        val value = Double.fromBits(0x4efa_080f_bf1f_3208L)
        assertMatchesJavaRoot("%020.12a", value)
        assertMatchesJavaRoot("%020.13a", value)
        assertMatchesJavaRoot("%024.17a", value)
    }

    @Test
    fun validLargeWidthIsNotRejectedByAnArbitraryPortableLimit() {
        assertEquals(1_000_001, "%1000001s".kiteFormat("x").length)
    }

    @Test
    fun floatPromotionUsesJavasCanonicalDecimalTieBreaking() {
        val values = listOf(
            Float.fromBits(0x4457_a3cd),
            Float.fromBits(0xc42f_d64d.toInt()),
        )
        values.forEach { value ->
            assertMatchesJavaRoot("%.17f", value)
            assertMatchesJavaRoot("%.17e", value)
            assertMatchesJavaRoot("%.17g", value)
        }
    }

    @Test
    fun integralBoundariesAndValidFlagCombinationsMatchJavaRootFormatter() {
        val decimalFormats = listOf(
            "%d",
            "%+d",
            "% d",
            "%(d",
            "%,d",
            "%20d",
            "%-20d",
            "%020d",
            "%+020d",
            "% 020d",
            "%(020d",
            "%,020d",
            "%(,020d",
            "%+,020d",
            "%-,20d",
            "%+,(024d",
        )
        val octalFormats = listOf(
            "%o",
            "%#o",
            "%20o",
            "%-20o",
            "%020o",
            "%#020o",
            "%#-24o",
        )
        val hexadecimalFormats = listOf(
            "%x",
            "%X",
            "%#x",
            "%#X",
            "%20x",
            "%-20X",
            "%020x",
            "%#020x",
            "%#020X",
            "%#-24X",
        )

        integralEdgeValues().forEach { value ->
            (decimalFormats + octalFormats + hexadecimalFormats).forEach { format ->
                assertMatchesJavaRoot(format, value)
            }
        }
    }

    private fun assertFloatingMatrixMatchesRoot(vararg formats: String) {
        floatingEdgeValues().forEach { value ->
            formats.forEach { format ->
                assertMatchesJavaRoot(format, value)
            }
        }
    }

    private fun assertMatchesJavaRoot(format: String, argument: Any) {
        val expected = java.lang.String.format(Locale.ROOT, format, argument)
        val actual = format.kiteFormat(argument)
        assertEquals(expected, actual, "$format with ${describe(argument)}")
    }

    private fun floatingEdgeValues(): List<Any> = listOf(
        0.0,
        -0.0,
        Double.MIN_VALUE,
        -Double.MIN_VALUE,
        Double.fromBits(0x000f_ffff_ffff_ffffL),
        -Double.fromBits(0x000f_ffff_ffff_ffffL),
        java.lang.Double.MIN_NORMAL,
        -java.lang.Double.MIN_NORMAL,
        Double.fromBits(java.lang.Double.MIN_NORMAL.toRawBits() + 1),
        0.1,
        -0.1,
        Double.fromBits(0.5.toRawBits() - 1),
        0.5,
        Double.fromBits(0.5.toRawBits() + 1),
        1.0,
        -1.0,
        1.03125,
        1.09375,
        1.96875,
        Double.fromBits(10.0.toRawBits() - 1),
        10.0,
        Double.fromBits(10.0.toRawBits() + 1),
        1.005,
        2.675,
        9.999999999999998,
        0.00009999,
        0.000099995,
        0.00009999995,
        1_234.5,
        9_999.5,
        999_999.5,
        java.lang.Math.scalb(1.0, -100),
        java.lang.Math.scalb(1.0, 100),
        Double.MAX_VALUE,
        -Double.MAX_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        0.0f,
        -0.0f,
        Float.MIN_VALUE,
        -Float.MIN_VALUE,
        Float.fromBits(0x007f_ffff),
        -Float.fromBits(0x007f_ffff),
        java.lang.Float.MIN_NORMAL,
        -java.lang.Float.MIN_NORMAL,
        Float.fromBits(java.lang.Float.MIN_NORMAL.toRawBits() + 1),
        0.1f,
        -0.1f,
        Float.fromBits(0.5f.toRawBits() - 1),
        0.5f,
        Float.fromBits(0.5f.toRawBits() + 1),
        1.0f,
        -1.0f,
        1.03125f,
        1.09375f,
        1.96875f,
        Float.fromBits(10.0f.toRawBits() - 1),
        10.0f,
        Float.fromBits(10.0f.toRawBits() + 1),
        1.005f,
        2.675f,
        16_777_215f,
        16_777_216f,
        java.lang.Math.scalb(1.0f, -20),
        java.lang.Math.scalb(1.0f, 20),
        Float.MAX_VALUE,
        -Float.MAX_VALUE,
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
    )

    private fun integralEdgeValues(): List<Any> = listOf(
        Byte.MIN_VALUE,
        (-1).toByte(),
        0.toByte(),
        1.toByte(),
        Byte.MAX_VALUE,
        Short.MIN_VALUE,
        (-12_345).toShort(),
        (-1).toShort(),
        0.toShort(),
        1.toShort(),
        Short.MAX_VALUE,
        Int.MIN_VALUE,
        -1_234_567,
        -1,
        0,
        1,
        1_234_567,
        Int.MAX_VALUE,
        Long.MIN_VALUE,
        -9_876_543_210L,
        -1L,
        0L,
        1L,
        9_876_543_210L,
        Long.MAX_VALUE,
    )

    private fun describe(argument: Any): String = when (argument) {
        is Double -> "Double(bits=0x${argument.toRawBits().toULong().toString(16)}, value=$argument)"
        is Float -> "Float(bits=0x${argument.toRawBits().toUInt().toString(16)}, value=$argument)"
        else -> "${argument::class.simpleName}($argument)"
    }

    private fun cases(): List<FormatCase> = listOf(
        FormatCase("literal"),
        FormatCase("%%|%n"),
        FormatCase("%b|%B|%b|%B|%b", true, true, null, null, 0),
        FormatCase("%h|%H|%h|%H", 255, 255, null, null),
        FormatCase("%s|%S|%s|%S", "Kotlin", "Kotlin", null, null),
        FormatCase("[%10.4s][%-10.4s]", "alphabet", "alphabet"),
        FormatCase("%c|%C|%c", 'a', 'q', 0x1F642),
        FormatCase("%c|%c|%c|%c|%C", 65.toByte(), 66.toShort(), 67, null, null),
        FormatCase("%d|%d|%d|%d", Byte.MIN_VALUE, Short.MIN_VALUE, Int.MIN_VALUE, Long.MIN_VALUE),
        FormatCase("%o|%o|%o|%o", (-1).toByte(), (-1).toShort(), -1, -1L),
        FormatCase("%x|%x|%x|%x", (-1).toByte(), (-1).toShort(), -1, -1L),
        FormatCase("%#08x|%#08X|%#06o", (-1).toByte(), (-1).toByte(), 10.toByte()),
        FormatCase("[%10d][%-10d][%+010d][% 010d][%(010d]", 123, 123, 123, 123, -123),
        FormatCase("%,d|%(,d|%0,12d", 123_456_789, -123_456_789, 1_234_567),
        FormatCase(
            "%e|%.2e|%#.0e|%+012.2E|%(012.2e",
            12.5,
            12.5,
            12.0,
            12.5,
            -12.5,
        ),
        FormatCase(
            "%f|%.2f|%#.0f|%,.2f|%+010.2f|%(010.2f",
            12.5,
            12.5,
            12.0,
            12_345.5,
            12.5,
            -12.5,
        ),
        FormatCase("%g|%.4g|%.1g|%.0g|%,.6g", 12_345.0, 12_345.0, 12_345.0, 12.0, 12_345.0),
        FormatCase("%a|%A|%.3a|%.0a|%+015.3A", 12.5, 12.5, 12.5, 12.5, 12.5),
        FormatCase("%.0f|%.0f|%.0f|%.1f", 0.5, 1.5, 2.5, -0.04),
        FormatCase("%.9f|%.9f", 1.23f, 1.23),
        FormatCase("%f|%e|%g|%a", -0.0, -0.0, -0.0, -0.0),
        FormatCase(
            "%f|%+e|%(g|%A",
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        ),
        FormatCase(
            "[%010f][%010f][%(010f]",
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        ),
        FormatCase("%2\$s %s %s", "alpha", "beta", "gamma"),
        FormatCase("%s %<S %2\$s %<s", "alpha", "beta"),
        FormatCase("%1\$s|%3\$d|%2\$s|%<S|%s", "alpha", "beta", 7),
        FormatCase("%s %% %<s %n %<S", "alpha"),
        FormatCase("%s", "first", "unused"),
        FormatCase("%d|%X|%E|%G|%A", null, null, null, null, null),
        FormatCase("%.2s|%.2b|%.2f", null, null, null),
        FormatCase("[%#08x][%+08d][%-8A]", null, null, null),
        FormatCase("%+x|% o|%(X", null, null, null),
    )

    private class FormatCase(
        val format: String,
        vararg arguments: Any?,
    ) {
        val arguments: Array<out Any?> = arguments
    }
}
