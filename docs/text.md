# Text utilities

Walkthroughs of the string extensions in `io.github.yuroyami.kitecore.text`. Each snippet is copy-pasteable, uses only real APIs from KiteCore 0.1.0, and shows the expected value in a comment.

All functions are extensions on `String` (plus one on `Char`), so a single import brings in the whole package:

```kotlin
import io.github.yuroyami.kitecore.text.*
```

## Case conversion

Convert identifiers between naming styles, capitalize prose, and derive slugs or initials. The case converters share one tokenizer that splits at underscores, hyphens, whitespace, and camel case transitions, so any input style feeds any output style.

```kotlin
"hello".capitalizeFirst()              // "Hello"
"Hello".decapitalizeFirst()            // "hello"
"user profile page".capitalizeWords()  // "User Profile Page"

"user_profile_id".toCamelCase()        // "userProfileId"
"user profile id".toPascalCase()       // "UserProfileId"
"userProfileId".toSnakeCase()          // "user_profile_id"
"UserProfileID".toKebabCase()          // "user-profile-id"

"HTTPRequestParser".splitCamelCase()   // [HTTP, Request, Parser]
"Hello, World! 2nd Edition".toSlug()   // "hello-world-2nd-edition"

"Ada Lovelace".initials()              // "AL"
"mary jane watson".initials(maxCount = 3)  // "MJW"
```

## Truncation and masking

Shorten strings for display, hide sensitive middles, normalize edges, and clean up whitespace. Truncation keeps the result within the limit, ellipsis included.

```kotlin
"The quick brown fox".truncate(12)                        // "The quick..."
"/Users/macbook/Documents/report.pdf".truncateMiddle(20)  // "/Users/ma...port.pdf"

"4111111111111111".mask(visibleEnd = 4)                // "************1111"
"secret-token".mask(visibleStart = 3, maskChar = '#')  // "sec#########"

"menu".padCenter(10)     // "   menu   "
"OK".padCenter(7, '-')   // "--OK---"

"example.com".prependIfMissing("https://")  // "https://example.com"
"report".appendIfMissing(".pdf")            // "report.pdf"
"HTTPS://example.com".removePrefixIgnoreCase("https://")  // "example.com"
"report.PDF".removeSuffixIgnoreCase(".pdf")               // "report"

"  a   lot \t of   space  ".collapseWhitespace()  // "a lot of space"
"1 234 567".removeWhitespace()                    // "1234567"
```

## Splitting and lines

Pull structured pieces out of a string, locate substrings, and work with lines without splitting into a list first. Functions that need a delimiter return null when it is absent, so a missing match never throws.

```kotlin
"key=value".splitToPair("=")      // ("key", "value")
"no delimiter".splitToPair("=")   // null
"<title>KiteCore</title>".substringBetween("<title>", "</title>")  // "KiteCore"

"banana".countOccurrences("an")   // 2
"a,b,c,d".indexOfNth(",", n = 2)  // 3

val log = "INFO start\nWARN retry\nERROR crash"
log.firstLine()    // "INFO start"
log.lastLine()     // "ERROR crash"
log.takeLines(2)   // "INFO start\nWARN retry"
log.dropLines(2)   // "ERROR crash"

"The quick brown fox jumps over the lazy dog".wrap(16)
// The quick brown
// fox jumps over
// the lazy dog

"line one\nline two".indent(2)  // "  line one\n  line two"
```

## Validation and parsing

Check what a string contains, then parse it without exception handling: every `to*OrDefault` function falls back to your default instead of throwing, and the `nullIf*` pair turns useless values into null for use with `?:`.

```kotlin
"42".isNumeric()           // true
"4.2".isNumeric()          // false (only ASCII digits pass)
"Kite".isAlpha()           // true
"Kite42".isAlphanumeric()  // true
"deadBEEF".isHex()         // true
"0xFF".isHex()             // false (no prefix support)
'e'.isVowel()              // true
'y'.isVowel()              // false

"42".toIntOrDefault(0)             // 42
"n/a".toIntOrDefault(0)            // 0
"9000000000".toLongOrDefault(-1L)  // 9000000000
"3.5".toFloatOrDefault(0f)         // 3.5
"2.75".toDoubleOrDefault(0.0)      // 2.75
"TRUE".toBooleanOrDefault(false)   // true
"yes".toBooleanOrDefault(false)    // false (only "true"/"false" match)

"   ".nullIfBlank()    // null
"".nullIfEmpty()       // null
"draft".nullIfBlank()  // "draft"

"Error: disk full".containsAny("error", "warn", ignoreCase = true)  // true
"user@example.com".containsAll("@", ".")                            // true
"PNG".equalsAnyIgnoreCase("png", "jpg", "webp")                     // true

"+1 (555) 010-9876".digitsOnly()  // "15550109876"
"Room 404B".lettersOnly()         // "RoomB"
```

## Sizing

Measure a string without allocating: `utf8Size` computes the encoded byte count from code points, and `lineCount` recognizes `\n`, `\r\n`, and `\r` as line breaks.

```kotlin
"héllo".utf8Size()       // 6 (é takes two bytes)
"🚀".utf8Size()          // 4 (surrogate pair)
"a\r\nb\rc".lineCount()  // 3
"".lineCount()           // 1 (an empty string is one line)
```

## Reference

Alphabetical index of every public API in the package. All are `String` extensions except `isVowel`, which extends `Char`.

| API | Description |
| --- | --- |
| `appendIfMissing(suffix)` | Appends `suffix` unless the string already ends with it. |
| `capitalizeFirst()` | Upper cases the first character, leaving the rest unchanged. |
| `capitalizeWords()` | Upper cases the first character of every whitespace separated word. |
| `collapseWhitespace()` | Trims the ends and collapses each whitespace run to one space. |
| `containsAll(vararg substrings, ignoreCase)` | True when every candidate substring occurs. |
| `containsAny(vararg substrings, ignoreCase)` | True when at least one candidate substring occurs. |
| `countOccurrences(substring)` | Counts non-overlapping occurrences of a substring. |
| `decapitalizeFirst()` | Lower cases the first character, leaving the rest unchanged. |
| `digitsOnly()` | Keeps only digit characters. |
| `dropLines(n)` | Removes the first `n` lines. |
| `equalsAnyIgnoreCase(vararg candidates)` | True when the string equals any candidate, ignoring case. |
| `firstLine()` | Content before the first line break. |
| `indent(spaces)` | Prepends the given number of spaces to every line. |
| `indexOfNth(substring, n)` | Index of the nth occurrence, counting from 1, or -1. |
| `initials(maxCount)` | Upper cased first characters of up to `maxCount` words. |
| `isAlpha()` | True when non-empty and every character is a letter. |
| `isAlphanumeric()` | True when non-empty and every character is a letter or digit. |
| `isHex()` | True when non-empty and every character is a hexadecimal digit. |
| `isNumeric()` | True when non-empty and every character is an ASCII digit. |
| `isVowel()` (on `Char`) | True for a, e, i, o, or u, in either case. |
| `lastLine()` | Content after the last line break. |
| `lettersOnly()` | Keeps only letter characters. |
| `lineCount()` | Number of lines, recognizing `\n`, `\r\n`, and `\r`. |
| `mask(visibleStart, visibleEnd, maskChar)` | Replaces all but the visible edges with a mask character. |
| `nullIfBlank()` | Null when blank, otherwise the string unchanged. |
| `nullIfEmpty()` | Null when empty, otherwise the string unchanged. |
| `padCenter(length, padChar)` | Pads both sides so the content sits centered at the target length. |
| `prependIfMissing(prefix)` | Prepends `prefix` unless the string already starts with it. |
| `removePrefixIgnoreCase(prefix)` | Removes a leading prefix, compared case insensitively. |
| `removeSuffixIgnoreCase(suffix)` | Removes a trailing suffix, compared case insensitively. |
| `removeWhitespace()` | Removes every whitespace character. |
| `splitCamelCase()` | Splits into camel case segments, keeping acronym runs together. |
| `splitToPair(delimiter)` | Splits at the first delimiter into a pair, or null when absent. |
| `substringBetween(start, end)` | Substring between two delimiters, or null when either is missing. |
| `takeLines(n)` | First `n` lines joined with `\n`. |
| `toBooleanOrDefault(default)` | Parses "true" or "false" ignoring case, or returns the default. |
| `toCamelCase()` | Converts to camelCase from any naming style. |
| `toDoubleOrDefault(default)` | Parses as `Double`, or returns the default. |
| `toFloatOrDefault(default)` | Parses as `Float`, or returns the default. |
| `toIntOrDefault(default)` | Parses as decimal `Int`, or returns the default. |
| `toKebabCase()` | Converts to kebab-case from any naming style. |
| `toLongOrDefault(default)` | Parses as decimal `Long`, or returns the default. |
| `toPascalCase()` | Converts to PascalCase from any naming style. |
| `toSlug()` | Lowercase ASCII slug with single hyphen separators. |
| `toSnakeCase()` | Converts to snake_case from any naming style. |
| `truncate(maxLength, ellipsis)` | Shortens to `maxLength`, replacing the tail with an ellipsis. |
| `truncateMiddle(maxLength, ellipsis)` | Shortens to `maxLength`, replacing the middle with an ellipsis. |
| `utf8Size()` | UTF-8 byte count computed without encoding a copy. |
| `wrap(maxLineLength)` | Word wraps so lines stay within the limit where possible. |
