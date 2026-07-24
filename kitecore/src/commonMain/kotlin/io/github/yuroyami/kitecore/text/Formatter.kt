/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.text

/**
 * Thrown when a portable JVM-style format string is malformed, lacks an
 * argument, or uses an argument that is incompatible with its conversion.
 *
 * The JVM exposes a family of `java.util.IllegalFormatException` subclasses.
 * Those types do not exist in common Kotlin, so KiteCore reports every
 * formatting failure through this single portable exception type.
 */
public class StringFormatException public constructor(message: String) : IllegalArgumentException(message)

/**
 * Uses this string as a JVM-style format string and substitutes [args].
 *
 * The implementation lives entirely in common code. It supports general
 * (`b`, `h`, `s`), character (`c`), integral (`d`, `o`, `x`), floating-point
 * (`e`, `f`, `g`, `a`), percent (`%`), and newline (`n`) conversions, including
 * their upper-case variants, argument indexes, relative argument reuse, flags,
 * width, and precision.
 *
 * Formatting uses ROOT-like, locale-independent punctuation and case rules.
 * Unicode casing follows the data available on the target runtime. `%n` always
 * emits LF. Java-only values and date/time conversions are unsupported. When a
 * target erases runtime numeric identities, ambiguous numbers use
 * conversion-directed Int-or-Double semantics. `%s` and `%h` delegate to the
 * target value's Kotlin `toString` and `hashCode`, respectively.
 *
 * @throws StringFormatException when the format or an argument is invalid.
 */
public fun String.format(vararg args: Any?): String = PortableStringFormatter(this, args).format()

/**
 * Formats [format] with [args] using KiteCore's portable JVM-style formatter.
 *
 * This companion extension provides the familiar `String.format(...)` call
 * shape in common Kotlin. See [String.format] for the supported conversions and
 * portability contract.
 *
 * @throws StringFormatException when the format or an argument is invalid.
 */
public fun String.Companion.format(format: String, vararg args: Any?): String = format.format(*args)

private const val FORMAT_FLAGS: String = "-#+ 0,(<"
private const val DIGIT_CHARS: String = "0123456789abcdef"

/*
 * Kotlin/JS can report an integer-valued Float or Double as Int after it enters
 * Any, while other runtimes may erase only the small integral tags. Keep the
 * probes independent so each runtime loses only the distinctions it actually
 * cannot observe.
 */
private val SMALL_INTEGRAL_TYPES_ARE_ERASED: Boolean =
    (1.toByte() as Any)::class == (1 as Any)::class ||
        (1.toShort() as Any)::class == (1 as Any)::class
private val FLOATING_TYPES_ARE_ERASED: Boolean =
    (1.5f as Any)::class == (1.5 as Any)::class
private val INTEGRAL_AND_FLOATING_TYPES_OVERLAP: Boolean =
    (1 as Any)::class == (1.0 as Any)::class

private class PortableStringFormatter(
    private val pattern: String,
    private val arguments: Array<out Any?>,
) {
    private val output: StringBuilder = StringBuilder(pattern.length + arguments.size * 8)
    private var ordinaryArgumentIndex: Int = 0
    private var previousArgumentIndex: Int = -1

    fun format(): String {
        var index = 0
        var literalStart = 0
        while (index < pattern.length) {
            if (pattern[index] != '%') {
                index++
                continue
            }
            appendOutput(pattern, literalStart, index)
            val parsed = parseSpecifier(index)
            appendOutput(formatSpecifier(parsed.specifier))
            index = parsed.endIndex
            literalStart = index
        }
        appendOutput(pattern, literalStart, pattern.length)
        return output.toString()
    }

    private fun parseSpecifier(percentIndex: Int): ParsedSpecifier {
        var index = percentIndex + 1
        if (index >= pattern.length) fail("Incomplete format specifier at index $percentIndex.")

        var explicitArgumentIndex: Int? = null
        val possibleIndexStart = index
        while (index < pattern.length && pattern[index].isAsciiDigit()) index++
        if (index > possibleIndexStart && index < pattern.length && pattern[index] == '$') {
            explicitArgumentIndex = parsePositiveNumber(
                pattern.substring(possibleIndexStart, index),
                "argument index",
            )
            index++
        } else {
            index = possibleIndexStart
        }

        val flags = StringBuilder()
        while (index < pattern.length && pattern[index] in FORMAT_FLAGS) {
            val flag = pattern[index++]
            if (flags.indexOf(flag.toString()) >= 0) {
                fail("Duplicate format flag '$flag' in specifier starting at index $percentIndex.")
            }
            flags.append(flag)
        }

        val widthStart = index
        while (index < pattern.length && pattern[index].isAsciiDigit()) index++
        val width = if (index > widthStart) {
            parseNonNegativeNumber(pattern.substring(widthStart, index), "width")
        } else {
            null
        }

        var precision: Int? = null
        if (index < pattern.length && pattern[index] == '.') {
            index++
            val precisionStart = index
            while (index < pattern.length && pattern[index].isAsciiDigit()) index++
            if (index == precisionStart) fail("Missing precision at index $precisionStart.")
            precision = parseNonNegativeNumber(pattern.substring(precisionStart, index), "precision")
        }

        if (index >= pattern.length) fail("Incomplete format specifier at index $percentIndex.")
        val conversion = pattern[index++]
        if (conversion == 't' || conversion == 'T') {
            if (index < pattern.length) index++
            fail("Date/time conversion '$conversion' is not supported by the common formatter.")
        }
        if (conversion !in "bBhHsScCdoxXeEfgGaA%n") {
            fail("Unknown format conversion '$conversion'.")
        }

        val source = pattern.substring(percentIndex, index)
        return ParsedSpecifier(
            FormatSpecifier(
                source = source,
                explicitArgumentIndex = explicitArgumentIndex,
                flags = flags.toString(),
                width = width,
                precision = precision,
                conversion = conversion,
            ),
            index,
        )
    }

    private fun formatSpecifier(specifier: FormatSpecifier): String {
        validateSpecifier(specifier)
        if (specifier.conversion == '%') {
            return padText("%", specifier.width, specifier.hasFlag('-'))
        }
        if (specifier.conversion == 'n') return "\n"

        val argumentIndex = when {
            specifier.hasFlag('<') -> previousArgumentIndex
            specifier.explicitArgumentIndex != null -> specifier.explicitArgumentIndex - 1
            else -> ordinaryArgumentIndex++
        }
        if (argumentIndex !in arguments.indices) {
            fail("Missing argument for format specifier '${specifier.source}'.")
        }
        previousArgumentIndex = argumentIndex

        val argument = arguments[argumentIndex]
        if (argument == null) return formatNull(specifier)
        return when (specifier.conversion.lowercaseChar()) {
            'b' -> formatBoolean(specifier, argument)
            'h' -> formatHash(specifier, argument)
            's' -> formatStringValue(specifier, argument)
            'c' -> formatCharacter(specifier, argument)
            'd', 'o', 'x' -> formatIntegral(specifier, argument)
            'e', 'f', 'g', 'a' -> formatFloatingPoint(specifier, argument)
            else -> fail("Unknown format conversion '${specifier.conversion}'.")
        }
    }

    private fun validateSpecifier(specifier: FormatSpecifier) {
        val conversion = specifier.conversion.lowercaseChar()
        val flagsWithoutReuse = specifier.flags.filterNot { it == '<' }
        if (specifier.hasFlag('<') && conversion in "%n") {
            fail("Flag '<' is incompatible with conversion '${specifier.conversion}'.")
        }
        if ('+' in flagsWithoutReuse && ' ' in flagsWithoutReuse) {
            fail("Flags '+' and ' ' cannot be combined in '${specifier.source}'.")
        }
        if ('-' in flagsWithoutReuse && '0' in flagsWithoutReuse) {
            fail("Flags '-' and '0' cannot be combined in '${specifier.source}'.")
        }
        if (('-' in flagsWithoutReuse || '0' in flagsWithoutReuse) && specifier.width == null) {
            fail("A width is required by the flags in '${specifier.source}'.")
        }

        val allowedFlags = when (conversion) {
            'b', 'h', 's', 'c' -> "-"
            'd' -> "-+ 0,("
            'o', 'x' -> "-#+ 0("
            'e' -> "-#+ 0("
            'f' -> "-#+ 0,("
            'g' -> "-+ 0,("
            'a' -> "-#+ 0"
            '%' -> "-"
            'n' -> ""
            else -> ""
        }
        for (flag in flagsWithoutReuse) {
            if (flag !in allowedFlags) {
                fail("Flag '$flag' is incompatible with conversion '${specifier.conversion}'.")
            }
        }

        when (conversion) {
            'c', 'd', 'o', 'x', '%', 'n' -> {
                if (specifier.precision != null) {
                    fail("Precision is not supported by conversion '${specifier.conversion}'.")
                }
            }
        }
        if (conversion == 'n') {
            if (specifier.flags.isNotEmpty()) fail("Flags are not supported by conversion 'n'.")
            if (specifier.width != null) fail("Width is not supported by conversion 'n'.")
        }
    }

    private fun formatNull(specifier: FormatSpecifier): String {
        var value = if (specifier.conversion.lowercaseChar() == 'b') "false" else "null"
        if (specifier.precision != null && value.length > specifier.precision) {
            value = value.substring(0, specifier.precision)
        }
        if (specifier.conversion.isUpperCase()) value = value.uppercase()
        return padText(value, specifier.width, specifier.hasFlag('-'))
    }

    private fun formatBoolean(specifier: FormatSpecifier, argument: Any): String {
        val value = if (argument is Boolean) argument.toString() else "true"
        return formatGeneralText(specifier, value)
    }

    private fun formatHash(specifier: FormatSpecifier, argument: Any): String {
        val hash = argument.hashCode()
        val value = unsignedBitsToString(hash.toLong(), 32, 4)
        return formatGeneralText(specifier, value)
    }

    private fun formatStringValue(specifier: FormatSpecifier, argument: Any): String =
        formatGeneralText(specifier, argument.toString())

    private fun formatGeneralText(specifier: FormatSpecifier, rawValue: String): String {
        var value = if (specifier.precision != null && rawValue.length > specifier.precision) {
            rawValue.substring(0, specifier.precision)
        } else {
            rawValue
        }
        if (specifier.conversion.isUpperCase()) value = value.uppercase()
        return padText(value, specifier.width, specifier.hasFlag('-'))
    }

    private fun formatCharacter(specifier: FormatSpecifier, argument: Any): String {
        var value = when {
            argument is Char -> argument.toString()
            else -> characterCodePoint(argument)?.let(::codePointToString)
        } ?: fail(
            "Conversion '${specifier.conversion}' requires Char, Byte, Short, or Int, " +
                "but received ${portableTypeName(argument)}.",
        )
        if (specifier.conversion.isUpperCase()) value = value.uppercase()
        return padText(value, specifier.width, specifier.hasFlag('-'))
    }

    private fun formatIntegral(specifier: FormatSpecifier, argument: Any): String {
        if (
            specifier.conversion.lowercaseChar() in "ox" &&
            specifier.flags.any { it == '+' || it == ' ' || it == '(' }
        ) {
            fail(
                "Flags '+', ' ', and '(' are unsupported for non-null " +
                    "${specifier.conversion} arguments.",
            )
        }
        val integral = integralArgument(argument)
            ?: fail(
                "Conversion '${specifier.conversion}' requires Byte, Short, Int, or Long, " +
                    "but received ${portableTypeName(argument)}.",
            )
        return when (specifier.conversion.lowercaseChar()) {
            'd' -> formatDecimalIntegral(specifier, integral.value)
            'o' -> {
                val digits = if (integral.value < 0L) {
                    unsignedBitsToString(integral.value, integral.bitWidth, 3)
                } else {
                    integral.value.toString(8)
                }
                val prefix = if (specifier.hasFlag('#')) "0" else ""
                formatNumeric(specifier, negative = false, prefix = prefix, magnitude = digits)
            }
            'x' -> {
                var digits = if (integral.value < 0L) {
                    unsignedBitsToString(integral.value, integral.bitWidth, 4)
                } else {
                    integral.value.toString(16)
                }
                var prefix = if (specifier.hasFlag('#')) "0x" else ""
                if (specifier.conversion.isUpperCase()) {
                    digits = digits.uppercase()
                    prefix = prefix.uppercase()
                }
                formatNumeric(specifier, negative = false, prefix = prefix, magnitude = digits)
            }
            else -> fail("Unknown integral conversion '${specifier.conversion}'.")
        }
    }

    private fun formatDecimalIntegral(specifier: FormatSpecifier, value: Long): String {
        val text = value.toString()
        var magnitude = if (value < 0L) text.substring(1) else text
        if (specifier.hasFlag(',')) magnitude = groupDecimalDigits(magnitude)
        return formatNumeric(specifier, negative = value < 0L, magnitude = magnitude)
    }

    private fun formatFloatingPoint(specifier: FormatSpecifier, argument: Any): String {
        val floating = floatingArgument(argument)
            ?: fail(
                "Conversion '${specifier.conversion}' requires Float or Double, " +
                    "but received ${portableTypeName(argument)}.",
            )
        val value = floating.value
        val negative = floating.isNegative
        val lowerConversion = specifier.conversion.lowercaseChar()

        if (value.isNaN()) {
            var result = "NaN"
            if (specifier.conversion.isUpperCase()) result = result.uppercase()
            return padText(result, specifier.width, specifier.hasFlag('-'))
        }
        if (value.isInfinite()) {
            var magnitude = "Infinity"
            if (specifier.conversion.isUpperCase()) magnitude = magnitude.uppercase()
            return formatNumeric(
                specifier,
                negative = negative,
                magnitude = magnitude,
                zeroPad = false,
            )
        }

        val precision = specifier.precision
        var prefix = ""
        var hexZeroPaddingIgnoredDigits = 0
        var magnitude = when (lowerConversion) {
            'f' -> {
                val fractionDigits = precision ?: 6
                var body = fixedDecimalMagnitude(value, fractionDigits, specifier.hasFlag('#'))
                if (specifier.hasFlag(',')) body = groupFixedDecimal(body)
                body
            }
            'e' -> scientificDecimalMagnitude(value, precision ?: 6, specifier.hasFlag('#'))
            'g' -> {
                val significantDigits = if (precision == null) 6 else maxOf(precision, 1)
                generalDecimalMagnitude(
                    value,
                    significantDigits,
                    grouping = specifier.hasFlag(','),
                )
            }
            'a' -> {
                val hexadecimal = hexadecimalMagnitude(floating, precision)
                prefix = "0x"
                hexZeroPaddingIgnoredDigits = hexadecimal.zeroPaddingIgnoredDigits
                hexadecimal.text
            }
            else -> fail("Unknown floating-point conversion '${specifier.conversion}'.")
        }
        if (specifier.conversion.isUpperCase()) {
            prefix = prefix.uppercase()
            magnitude = magnitude.uppercase()
        }
        return formatNumeric(
            specifier,
            negative = negative,
            prefix = prefix,
            magnitude = magnitude,
            hexZeroPaddingIgnoredDigits = hexZeroPaddingIgnoredDigits,
        )
    }

    private fun formatNumeric(
        specifier: FormatSpecifier,
        negative: Boolean,
        prefix: String = "",
        magnitude: String,
        zeroPad: Boolean = specifier.hasFlag('0'),
        hexZeroPaddingIgnoredDigits: Int = 0,
    ): String {
        val parentheses = negative && specifier.hasFlag('(')
        val leading = when {
            parentheses -> "("
            negative -> "-"
            specifier.hasFlag('+') -> "+"
            specifier.hasFlag(' ') -> " "
            else -> ""
        }
        val trailing = if (parentheses) ")" else ""
        val baseLength = leading.length + prefix.length + magnitude.length + trailing.length
        val width = specifier.width ?: 0
        val effectiveBaseLength = if (hexZeroPaddingIgnoredDigits > 0 && zeroPad) {
            /*
             * java.util.Formatter calculates hexadecimal zero padding as if
             * the fraction ended at its last meaningful hexadecimal digit,
             * then appends any precision-only trailing zeroes.
             */
            baseLength - hexZeroPaddingIgnoredDigits
        } else {
            baseLength
        }
        if (width <= effectiveBaseLength) return leading + prefix + magnitude + trailing

        val paddingCount = width - effectiveBaseLength
        if (paddingCount <= 0) return leading + prefix + magnitude + trailing
        val padding = if (zeroPad && !specifier.hasFlag('-')) {
            "0".repeat(paddingCount)
        } else {
            " ".repeat(paddingCount)
        }
        return when {
            specifier.hasFlag('-') -> leading + prefix + magnitude + trailing + padding
            zeroPad -> leading + prefix + padding + magnitude + trailing
            else -> padding + leading + prefix + magnitude + trailing
        }
    }

    private fun appendOutput(value: String) {
        output.append(value)
    }

    private fun appendOutput(value: String, startIndex: Int, endIndex: Int) {
        output.append(value, startIndex, endIndex)
    }
}

private data class ParsedSpecifier(
    val specifier: FormatSpecifier,
    val endIndex: Int,
)

private data class FormatSpecifier(
    val source: String,
    val explicitArgumentIndex: Int?,
    val flags: String,
    val width: Int?,
    val precision: Int?,
    val conversion: Char,
) {
    fun hasFlag(flag: Char): Boolean = flag in flags
}

private data class IntegralArgument(
    val value: Long,
    val bitWidth: Int,
)

private data class FloatingArgument(
    val value: Double,
    val isFloat: Boolean,
    val isNegative: Boolean,
)

private enum class NumericKind {
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
}

private fun numericKind(value: Any): NumericKind? {
    val type = value::class
    return when (type) {
        Byte::class -> NumericKind.BYTE
        Short::class -> NumericKind.SHORT
        Int::class -> NumericKind.INT
        Long::class -> NumericKind.LONG
        Float::class -> NumericKind.FLOAT
        Double::class -> NumericKind.DOUBLE
        else -> null
    }
}

private fun characterCodePoint(value: Any): Int? {
    val kind = numericKind(value)
    val number = value as? Number ?: return null
    if (INTEGRAL_AND_FLOATING_TYPES_OVERLAP) {
        if (kind == NumericKind.LONG || kind == null) return null
        return integralAsInt(number)?.value?.toInt()
    }
    return when (kind) {
        NumericKind.BYTE -> if (SMALL_INTEGRAL_TYPES_ARE_ERASED) number.toInt() else number.toByte().toInt()
        NumericKind.SHORT -> if (SMALL_INTEGRAL_TYPES_ARE_ERASED) number.toInt() else number.toShort().toInt()
        NumericKind.INT -> number.toInt()
        else -> null
    }
}

private fun integralArgument(value: Any): IntegralArgument? {
    val kind = numericKind(value)
    val number = value as? Number ?: return null
    if (INTEGRAL_AND_FLOATING_TYPES_OVERLAP) {
        if (kind == NumericKind.LONG || kind == null) {
            return if (kind == NumericKind.LONG) IntegralArgument(number.toLong(), 64) else null
        }
        return integralAsInt(number)
    }
    return when (kind) {
        NumericKind.BYTE -> if (SMALL_INTEGRAL_TYPES_ARE_ERASED) {
            IntegralArgument(number.toInt().toLong(), 32)
        } else {
            IntegralArgument(number.toByte().toLong(), 8)
        }
        NumericKind.SHORT -> if (SMALL_INTEGRAL_TYPES_ARE_ERASED) {
            IntegralArgument(number.toInt().toLong(), 32)
        } else {
            IntegralArgument(number.toShort().toLong(), 16)
        }
        NumericKind.INT -> IntegralArgument(number.toInt().toLong(), 32)
        NumericKind.LONG -> IntegralArgument(number.toLong(), 64)
        else -> null
    }
}

private fun integralAsInt(value: Number): IntegralArgument? {
    val number = value.toDouble()
    if (
        !number.isFinite() ||
        number % 1.0 != 0.0 ||
        number < Int.MIN_VALUE ||
        number > Int.MAX_VALUE
    ) {
        return null
    }
    return IntegralArgument(number.toInt().toLong(), 32)
}

private fun floatingArgument(value: Any): FloatingArgument? {
    val kind = numericKind(value)
    if (!INTEGRAL_AND_FLOATING_TYPES_OVERLAP && !FLOATING_TYPES_ARE_ERASED) {
        if (kind == NumericKind.FLOAT) {
            val number = (value as Number).toFloat()
            return FloatingArgument(
                value = number.toDouble(),
                isFloat = true,
                isNegative = number.toBits() < 0,
            )
        }
        if (kind != NumericKind.DOUBLE) return null
    } else if (
        kind == NumericKind.LONG ||
        kind == null ||
        (!INTEGRAL_AND_FLOATING_TYPES_OVERLAP && kind !in listOf(NumericKind.FLOAT, NumericKind.DOUBLE))
    ) {
        return null
    }
    if (!FLOATING_TYPES_ARE_ERASED && kind == NumericKind.FLOAT) {
        val number = (value as Number).toFloat()
        return FloatingArgument(
            value = number.toDouble(),
            isFloat = true,
            isNegative = number.toBits() < 0,
        )
    }
    val number = (value as Number).toDouble()
    return FloatingArgument(
        value = number,
        isFloat = false,
        isNegative = number.toBits() < 0L,
    )
}

private fun fixedDecimalMagnitude(value: Double, fractionDigits: Int, alternate: Boolean): String {
    val units = roundedDecimalUnits(exactDecimal(value), fractionDigits)
    if (fractionDigits == 0) return if (alternate) "$units." else units
    val padded = units.padStart(fractionDigits + 1, '0')
    val point = padded.length - fractionDigits
    return padded.substring(0, point) + "." + padded.substring(point)
}

private fun scientificDecimalMagnitude(value: Double, fractionDigits: Int, alternate: Boolean): String {
    val significantDigits = fractionDigits + 1
    val rounded = roundedSignificant(exactDecimal(value), significantDigits)
    val digits = rounded.digits.padEnd(significantDigits, '0')
    val significand = buildString(significantDigits + 1) {
        append(digits[0])
        if (fractionDigits > 0 || alternate) {
            append('.')
            if (fractionDigits > 0) append(digits, 1, significantDigits)
        }
    }
    return significand + decimalExponent(rounded.exponent)
}

private fun generalDecimalMagnitude(value: Double, significantDigits: Int, grouping: Boolean): String {
    val decimal = exactDecimal(value)
    val rounded = roundedSignificant(decimal, significantDigits)
    return if (rounded.exponent < -4 || rounded.exponent >= significantDigits) {
        val digits = rounded.digits.padEnd(significantDigits, '0')
        buildString(significantDigits + 7) {
            append(digits[0])
            if (significantDigits > 1) {
                append('.')
                append(digits, 1, significantDigits)
            }
            append(decimalExponent(rounded.exponent))
        }
    } else {
        val fractionDigits = significantDigits - rounded.exponent - 1
        var fixed = fixedDecimalMagnitude(value, fractionDigits, alternate = false)
        if (grouping) fixed = groupFixedDecimal(fixed)
        fixed
    }
}

private data class ExactDecimal(
    val digits: String,
    val scale: Int,
)

private data class RoundedSignificant(
    val digits: String,
    val exponent: Int,
)

private fun exactDecimal(value: Double): ExactDecimal {
    if (value == 0.0) return ExactDecimal("0", 0)
    val exact = exactBinaryDecimal(value)
    val absoluteBits = value.toBits() and Long.MAX_VALUE
    for (significantDigits in 2..17) {
        val rounded = roundedSignificant(exact, significantDigits, tiesToEven = true)
        val candidate = buildString(significantDigits + 8) {
            append(rounded.digits[0])
            append('.')
            append(rounded.digits, 1, rounded.digits.length)
            append('e')
            append(rounded.exponent)
        }
        if ((candidate.toDouble().toBits() and Long.MAX_VALUE) == absoluteBits) {
            return ExactDecimal(
                digits = rounded.digits,
                scale = rounded.digits.length - rounded.exponent - 1,
            )
        }
    }
    return exact
}

/*
 * java.util.Formatter starts from the shortest decimal that identifies the
 * Double, rather than from every digit of its exact binary value. The exact
 * representation below is used to derive that shortest round-tripping decimal
 * without delegating formatting to a platform API.
 */
private fun exactBinaryDecimal(value: Double): ExactDecimal {
    val bits = value.toBits() and Long.MAX_VALUE
    val exponentBits = ((bits ushr 52) and 0x7ffL).toInt()
    val fraction = bits and 0x000f_ffff_ffff_ffffL
    val significand: Long
    val binaryExponent: Int
    if (exponentBits == 0) {
        significand = fraction
        binaryExponent = -1074
    } else {
        significand = (1L shl 52) or fraction
        binaryExponent = exponentBits - 1023 - 52
    }

    var digits = significand.toString()
    var scale = 0
    if (binaryExponent >= 0) {
        repeat(binaryExponent) { digits = multiplyDecimalString(digits, 2) }
    } else {
        scale = -binaryExponent
        repeat(scale) { digits = multiplyDecimalString(digits, 5) }
    }
    while (scale > 0 && digits.length > 1 && digits.endsWith('0')) {
        digits = digits.dropLast(1)
        scale--
    }
    return ExactDecimal(digits, scale)
}

private fun roundedDecimalUnits(decimal: ExactDecimal, fractionDigits: Int): String {
    val shift = fractionDigits - decimal.scale
    if (shift >= 0) return decimal.digits + "0".repeat(shift)
    val discarded = -shift
    if (discarded > decimal.digits.length) return "0"
    if (discarded == decimal.digits.length) {
        return if (decimal.digits[0] >= '5') "1" else "0"
    }
    val keepLength = decimal.digits.length - discarded
    var kept = decimal.digits.substring(0, keepLength)
    if (decimal.digits[keepLength] >= '5') kept = incrementDecimalString(kept)
    return kept
}

private fun roundedSignificant(
    decimal: ExactDecimal,
    significantDigits: Int,
    tiesToEven: Boolean = false,
): RoundedSignificant {
    if (decimal.digits == "0") {
        return RoundedSignificant("0".repeat(significantDigits), 0)
    }
    var exponent = decimal.digits.length - decimal.scale - 1
    var digits = if (decimal.digits.length <= significantDigits) {
        decimal.digits.padEnd(significantDigits, '0')
    } else {
        var kept = decimal.digits.substring(0, significantDigits)
        if (shouldRoundUp(decimal.digits, significantDigits, tiesToEven)) {
            kept = incrementDecimalString(kept)
        }
        if (kept.length > significantDigits) {
            exponent++
            kept = "1" + "0".repeat(significantDigits - 1)
        }
        kept
    }
    if (digits.length < significantDigits) digits = digits.padEnd(significantDigits, '0')
    return RoundedSignificant(digits, exponent)
}

private fun shouldRoundUp(digits: String, keepLength: Int, tiesToEven: Boolean): Boolean {
    val firstDiscarded = digits[keepLength]
    if (firstDiscarded != '5') return firstDiscarded > '5'
    if (!tiesToEven) return true
    for (index in keepLength + 1 until digits.length) {
        if (digits[index] != '0') return true
    }
    return (digits[keepLength - 1] - '0') % 2 != 0
}

private data class HexadecimalMagnitude(
    val text: String,
    val zeroPaddingIgnoredDigits: Int,
)

private fun hexadecimalMagnitude(
    argument: FloatingArgument,
    requestedPrecision: Int?,
): HexadecimalMagnitude {
    val value = argument.value
    if (value == 0.0) {
        val digits = if (requestedPrecision == null) 1 else maxOf(requestedPrecision, 1)
        return HexadecimalMagnitude(
            text = "0." + "0".repeat(digits) + "p0",
            zeroPaddingIgnoredDigits = if (requestedPrecision == null) 0 else digits - 1,
        )
    }

    val normalized = normalizedHexadecimal(argument)
    if (
        normalized.denormalizedDoubleFraction != null &&
        (requestedPrecision == null || requestedPrecision >= 13)
    ) {
        val fullFraction = normalized.denormalizedDoubleFraction.toString(16).padStart(13, '0')
        var fraction = fullFraction
        fraction = if (requestedPrecision == null) {
            fraction.trimEnd('0').ifEmpty { "0" }
        } else {
            fraction + "0".repeat(requestedPrecision - 13)
        }
        return HexadecimalMagnitude(
            text = "0.${fraction}p-1022",
            zeroPaddingIgnoredDigits = if (requestedPrecision == null) {
                0
            } else {
                precisionOnlyTrailingZeroCount(fraction)
            },
        )
    }

    val precision = requestedPrecision?.let { maxOf(it, 1) }
    if (precision == null) {
        val fraction = normalized.fractionBits
            .toString(16)
            .padStart(13, '0')
            .trimEnd('0')
            .ifEmpty { "0" }
        return HexadecimalMagnitude(
            text = "1.${fraction}p${normalized.exponent}",
            zeroPaddingIgnoredDigits = 0,
        )
    }

    var exponent = normalized.exponent
    val fraction: String
    if (precision >= 13) {
        fraction = normalized.fractionBits
            .toString(16)
            .padStart(13, '0') + "0".repeat(precision - 13)
    } else {
        val shift = 52 - precision * 4
        val significand = (1L shl 52) or normalized.fractionBits
        var kept = significand ushr shift
        val remainderMask = (1L shl shift) - 1L
        val remainder = significand and remainderMask
        val halfway = 1L shl (shift - 1)
        if (remainder > halfway || (remainder == halfway && kept and 1L != 0L)) kept++
        val carryLimit = 2L shl (precision * 4)
        if (kept == carryLimit) {
            exponent++
            kept = 1L shl (precision * 4)
        }
        val fractionMask = (1L shl (precision * 4)) - 1L
        fraction = (kept and fractionMask).toString(16).padStart(precision, '0')
    }
    return HexadecimalMagnitude(
        text = "1.${fraction}p$exponent",
        zeroPaddingIgnoredDigits = precisionOnlyTrailingZeroCount(fraction),
    )
}

private fun precisionOnlyTrailingZeroCount(fraction: String): Int {
    val meaningfulDigits = maxOf(fraction.trimEnd('0').length, 1)
    return fraction.length - meaningfulDigits
}

private data class NormalizedHexadecimal(
    val exponent: Int,
    val fractionBits: Long,
    val denormalizedDoubleFraction: Long?,
)

private fun normalizedHexadecimal(argument: FloatingArgument): NormalizedHexadecimal {
    if (argument.isFloat) {
        val bits = argument.value.toFloat().toBits() and Int.MAX_VALUE
        val exponentBits = (bits ushr 23) and 0xff
        val fraction = bits and 0x007f_ffff
        if (exponentBits != 0) {
            return NormalizedHexadecimal(
                exponent = exponentBits - 127,
                fractionBits = fraction.toLong() shl 29,
                denormalizedDoubleFraction = null,
            )
        }
        val highest = highestSetBit(fraction.toLong())
        val leading = 1L shl highest
        val remainder = fraction.toLong() xor leading
        return NormalizedHexadecimal(
            exponent = highest - 149,
            fractionBits = remainder shl (52 - highest),
            denormalizedDoubleFraction = null,
        )
    }

    val bits = argument.value.toBits() and Long.MAX_VALUE
    val exponentBits = ((bits ushr 52) and 0x7ffL).toInt()
    val fraction = bits and 0x000f_ffff_ffff_ffffL
    if (exponentBits != 0) {
        return NormalizedHexadecimal(
            exponent = exponentBits - 1023,
            fractionBits = fraction,
            denormalizedDoubleFraction = null,
        )
    }
    val highest = highestSetBit(fraction)
    val leading = 1L shl highest
    val remainder = fraction xor leading
    return NormalizedHexadecimal(
        exponent = highest - 1074,
        fractionBits = remainder shl (52 - highest),
        denormalizedDoubleFraction = fraction,
    )
}

private fun highestSetBit(value: Long): Int {
    var bit = 63
    while (bit > 0 && (value and (1L shl bit)) == 0L) bit--
    return bit
}

private fun multiplyDecimalString(value: String, factor: Int): String {
    val output = StringBuilder(value.length + 1)
    var carry = 0
    for (index in value.lastIndex downTo 0) {
        val product = (value[index] - '0') * factor + carry
        output.append(('0'.code + product % 10).toChar())
        carry = product / 10
    }
    while (carry > 0) {
        output.append(('0'.code + carry % 10).toChar())
        carry /= 10
    }
    return output.reverse().toString()
}

private fun incrementDecimalString(value: String): String {
    val chars = value.toCharArray()
    var index = chars.lastIndex
    while (index >= 0 && chars[index] == '9') {
        chars[index] = '0'
        index--
    }
    if (index < 0) return "1" + chars.concatToString()
    chars[index]++
    return chars.concatToString()
}

private fun decimalExponent(exponent: Int): String {
    val sign = if (exponent < 0) '-' else '+'
    val digits = absoluteInt(exponent).toString().padStart(2, '0')
    return "e$sign$digits"
}

private fun groupDecimalDigits(value: String): String = buildString(value.length + value.length / 3) {
    for (index in value.indices) {
        if (index > 0 && (value.length - index) % 3 == 0) append(',')
        append(value[index])
    }
}

private fun groupFixedDecimal(value: String): String {
    val point = value.indexOf('.')
    val integerEnd = if (point >= 0) point else value.length
    val integer = groupDecimalDigits(value.substring(0, integerEnd))
    return if (point >= 0) integer + value.substring(point) else integer
}

private fun unsignedBitsToString(value: Long, bitWidth: Int, bitsPerDigit: Int): String {
    val digitCount = (bitWidth + bitsPerDigit - 1) / bitsPerDigit
    val mask = (1L shl bitsPerDigit) - 1L
    var bits = if (bitWidth == 64) value else value and ((1L shl bitWidth) - 1L)
    val chars = CharArray(digitCount)
    for (index in digitCount - 1 downTo 0) {
        chars[index] = DIGIT_CHARS[(bits and mask).toInt()]
        bits = bits ushr bitsPerDigit
    }
    var start = 0
    while (start < chars.lastIndex && chars[start] == '0') start++
    return chars.concatToString(start, chars.size)
}

private fun codePointToString(codePoint: Int): String {
    if (codePoint < 0 || codePoint > 0x10ffff) {
        fail("Invalid Unicode code point 0x${codePoint.toUInt().toString(16)}.")
    }
    if (codePoint <= 0xffff) return codePoint.toChar().toString()
    val adjusted = codePoint - 0x10000
    val high = ((adjusted ushr 10) + 0xd800).toChar()
    val low = ((adjusted and 0x3ff) + 0xdc00).toChar()
    return "$high$low"
}

private fun padText(value: String, width: Int?, leftJustify: Boolean): String {
    if (width == null || width <= value.length) return value
    val padding = " ".repeat(width - value.length)
    return if (leftJustify) value + padding else padding + value
}

private fun parsePositiveNumber(value: String, label: String): Int {
    val parsed = parseNonNegativeNumber(value, label)
    if (parsed == 0) fail("Format $label must be positive.")
    return parsed
}

private fun parseNonNegativeNumber(value: String, label: String): Int {
    var result = 0
    for (character in value) {
        val digit = character - '0'
        if (result > (Int.MAX_VALUE - digit) / 10) fail("Format $label is too large.")
        result = result * 10 + digit
    }
    return result
}

private fun portableTypeName(value: Any): String = value::class.simpleName ?: "unknown type"

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun absoluteInt(value: Int): Int = if (value < 0) -value else value

private fun fail(message: String): Nothing = throw StringFormatException(message)
