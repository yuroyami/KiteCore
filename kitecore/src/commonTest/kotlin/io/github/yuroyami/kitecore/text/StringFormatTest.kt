/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringFormatTest {

    @Test
    fun receiverAndCompanionFormsProduceTheSameResult() {
        assertEquals("Hello, KiteCore!", "Hello, %s!".format("KiteCore"))
        assertEquals("Hello, KiteCore!", String.format("Hello, %s!", "KiteCore"))
        assertEquals("plain text", "plain text".format())
        assertEquals("first", "%s".format("first", "unused"))
    }

    @Test
    fun generalConversionsMatchRootFormatterSemantics() {
        assertEquals(
            "true|TRUE|false|FALSE|true",
            "%b|%B|%b|%B|%b".format(true, true, null, null, 0),
        )
        assertEquals("ff|FF|null|NULL", "%h|%H|%h|%H".format(255, 255, null, null))
        assertEquals(
            "Kotlin|KOTLIN|null|NULL",
            "%s|%S|%s|%S".format("Kotlin", "Kotlin", null, null),
        )
        assertEquals(
            "[      alph][alph      ]",
            "[%10.4s][%-10.4s]".format("alphabet", "alphabet"),
        )
        assertEquals("STRASSE|É", "%S|%C".format("straße", 'é'))
    }

    @Test
    fun characterConversionsSupportPortableCodePoints() {
        assertEquals("a|Q|🙂", "%c|%C|%c".format('a', 'q', 0x1F642))
        assertEquals("[    a][a    ]", "[%5c][%-5c]".format('a', 'a'))
        assertEquals("A|B|C|null|NULL", "%c|%c|%c|%c|%C".format(65.toByte(), 66.toShort(), 67, null, null))
    }

    @Test
    fun decimalIntegerConversionsHonorSignsGroupingAndPadding() {
        assertEquals(
            "-128|-32768|-2147483648|-9223372036854775808",
            "%d|%d|%d|%d".format(Byte.MIN_VALUE, Short.MIN_VALUE, Int.MIN_VALUE, Long.MIN_VALUE),
        )
        assertEquals(
            "[       123][123       ][+000000123][ 000000123][(00000123)]",
            "[%10d][%-10d][%+010d][% 010d][%(010d]".format(123, 123, 123, 123, -123),
        )
        assertEquals(
            "123,456,789|(123,456,789)|0001,234,567",
            "%,d|%(,d|%0,12d".format(123_456_789, -123_456_789, 1_234_567),
        )
        assertEquals("null|NULL", "%d|%X".format(null, null))
    }

    @Test
    fun octalAndHexadecimalHonorPrefixesCaseAndPadding() {
        assertEquals(
            "012|0x1a|0X1A",
            "%#o|%#x|%#X".format(10, 26, 26),
        )
        assertEquals(
            "0x00001a|0X00001A|000012",
            "%#08x|%#08X|%#06o".format(26, 26, 10),
        )
    }

    @Test
    fun decimalAndScientificFloatingPointFormattingMatchesJvmGoldens() {
        assertEquals(
            "12.500000|12.50|12.|12,345.50|+000012.50|(00012.50)",
            "%f|%.2f|%#.0f|%,.2f|%+010.2f|%(010.2f"
                .format(12.5, 12.5, 12.0, 12_345.5, 12.5, -12.5),
        )
        assertEquals(
            "1.250000e+01|1.25e+01|1.e+01|+0001.25E+01|(001.25e+01)",
            "%e|%.2e|%#.0e|%+012.2E|%(012.2e"
                .format(12.5, 12.5, 12.0, 12.5, -12.5),
        )
        assertEquals("1|2|3|-0.0", "%.0f|%.0f|%.0f|%.1f".format(0.5, 1.5, 2.5, -0.04))
    }

    @Test
    fun generalAndHexadecimalFloatingPointFormattingMatchesJvmGoldens() {
        assertEquals(
            "12345.0|1.235e+04|1e+04|1e+01|12,345.0",
            "%g|%.4g|%.1g|%.0g|%,.6g".format(12_345.0, 12_345.0, 12_345.0, 12.0, 12_345.0),
        )
        assertEquals(
            "0x1.9p3|0X1.9P3|0x1.900p3|0x1.9p3|+0X00000001.900P3",
            "%a|%A|%.3a|%.0a|%+015.3A".format(12.5, 12.5, 12.5, 12.5, 12.5),
        )
        assertEquals(
            "-0.000000|-0.000000e+00|-0.00000|-0x0.0p0",
            "%f|%e|%g|%a".format(-0.0, -0.0, -0.0, -0.0),
        )
    }

    @Test
    fun floatingPointBoundaryGoldensStayIdenticalAcrossTargets() {
        assertEquals(
            "4.900000e-324|0.10000000000000000|0x0.0000000000001p-1022",
            "%e|%.17f|%.13a".format(Double.MIN_VALUE, 0.1, Double.MIN_VALUE),
        )
        assertEquals(
            "0x1.0p0|0x1.2p0|0x1.0p1",
            "%.1a|%.1a|%.1a".format(1.03125, 1.09375, 1.96875),
        )
    }

    @Test
    fun erasedRuntimeNumericGroupsUseConversionDirectedSemantics() {
        val byteType = (1.toByte() as Any)::class
        val byteWidthIsErased =
            byteType == (1.toShort() as Any)::class || byteType == (1 as Any)::class
        assertEquals(
            if (byteWidthIsErased) "ffffff80" else "80",
            "%x".format((-128).toByte()),
        )

        val boxedFloat = 1.23f as Any
        val widenedFloat = (boxedFloat as Number).toDouble()
        val expectedFloat = if (widenedFloat.toBits() == 1.23.toBits()) {
            "1.23000000000000000"
        } else {
            "1.23000001907348630"
        }
        assertEquals(expectedFloat, "%.17f".format(boxedFloat))

        val integerAndDoubleShareType = (1 as Any)::class == (1.0 as Any)::class
        if (integerAndDoubleShareType) {
            assertEquals("1.000000", "%f".format(1))
        } else {
            assertFormatFails("%f", 1)
        }
    }

    @Test
    fun nonFiniteFloatingPointValuesUseJvmSpellingAndIgnoreZeroPadding() {
        assertEquals(
            "NaN|+Infinity|(Infinity)|-INFINITY",
            "%f|%+e|%(g|%A".format(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
            ),
        )
        assertEquals(
            "[       NaN][  Infinity][(Infinity)]",
            "[%010f][%010f][%(010f]".format(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
            ),
        )
    }

    @Test
    fun nullPlaceholdersAreTruncatedAndSpacePaddedLikeText() {
        assertEquals("nu|fa|nu", "%.2s|%.2b|%.2f".format(null, null, null))
        assertEquals(
            "[    null][    null][NULL    ]",
            "[%#08x][%+08d][%-8A]".format(null, null, null),
        )
        assertEquals(
            "null|null|null|NULL",
            "%+x|% x|%(o|%+X".format(null, null, null, null),
        )
    }

    @Test
    fun explicitOrdinaryAndRelativeIndexesFollowJvmRules() {
        assertEquals("beta alpha beta", "%2\$s %s %s".format("alpha", "beta", "gamma"))
        assertEquals("alpha ALPHA beta beta", "%s %<S %2\$s %<s".format("alpha", "beta"))
        assertEquals(
            "alpha|7|beta|BETA|alpha",
            "%1\$s|%3\$d|%2\$s|%<S|%s".format("alpha", "beta", 7),
        )
        assertEquals("alpha % alpha \n ALPHA", "%s %% %<s %n %<S".format("alpha"))
    }

    @Test
    fun percentAndNewlineHavePortableWidthAndLineEndingSemantics() {
        assertEquals("[    %][%    ]\nend", "[%5%][%-5%]%nend".format())
        assertEquals("%\nvalue", "%%%n%s".format("value"))
    }

    @Test
    fun malformedFormatsAndInvalidArgumentsUseTheCommonException() {
        assertFormatFails("%")
        assertFormatFails("%q")
        assertFormatFails("%tY", 0L)
        assertFormatFails("%TY", 0L)
        assertFormatFails("%0d", 1)
        assertFormatFails("%-s", "value")
        assertFormatFails("%--5s", "value")
        assertFormatFails("%+ d", 1)
        assertFormatFails("%#s", "value")
        assertFormatFails("%.2d", 1)
        assertFormatFails("%2147483648s", "value")
        assertFormatFails("%0\$s", "value")
        assertFormatFails("%<s", "value")
        assertFormatFails("%2\$s", "only one")
        assertFormatFails("%d", "1")
        assertFormatFails("%d", 1.5)
        assertFormatFails("%+x", 1)
        assertFormatFails("% x", 1)
        assertFormatFails("%(o", 1)
        assertFormatFails("%c", 65L)
        assertFormatFails("%c", -1)
        assertFormatFails("%c", 0x110000)
        assertFormatFails("%d", 1u)
        assertFormatFails("%f", 1u)
        assertFormatFails("%.1%")
        assertFormatFails("%<%")
        assertFormatFails("%<n")
        assertFormatFails("%s%<%", "value")
        assertFormatFails("%s%<n", "value")
        assertFormatFails("%-n")
    }

    private fun assertFormatFails(format: String, vararg arguments: Any?) {
        assertFailsWith<StringFormatException>(format) {
            format.format(*arguments)
        }
    }
}
