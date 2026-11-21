package com.teamwork.pricehunter.core.intelligence

object PersianNormalizer {
    private const val ZWNJ = '\u200C'
    private const val ARABIC_TATWEEL = '\u0640'
    private val DIACRITICS_AND_BIDI_REGEX = Regex("[\u064B-\u065F\u0670\u200E\u200F\u202A-\u202E]")
    private val CONSECUTIVE_ZWNJ = Regex("\u200C+")
    private val ZWNJ_WITH_SPACE = Regex("[\\s\u200C]*\\s[\\s\u200C]*")
    private val MULTI_SPACE = Regex("\\s+")

    fun normalizeDigits(input: String): String {
        if (input.isEmpty()) return ""
        val chars = CharArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            chars[i] = when (c) {
                in '\u06F0'..'\u06F9' -> (c - '\u06F0' + '0'.code).toChar()
                in '\u0660'..'\u0669' -> (c - '\u0660' + '0'.code).toChar()
                '\u066B' -> '.'
                else -> c
            }
        }
        return String(chars)
    }

    fun normalizeCharacters(input: String): String {
        if (input.isEmpty()) return ""
        val chars = CharArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            chars[i] = when (c) {
                '\u064A', '\u0649' -> '\u06CC' // ي, ى -> ی
                '\u0643' -> '\u06A9'           // ك -> ک
                '\u0629', '\u06C0' -> '\u0647' // ة, ۀ -> ه
                '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627' // آ, أ, إ, ٱ -> ا
                '\u0624' -> '\u0648'           // ؤ -> و
                '\u0626' -> '\u06CC'           // ئ -> ی
                else -> c
            }
        }
        return String(chars)
    }

    fun stripDiacritics(input: String): String {
        if (input.isEmpty()) return ""
        return input.replace(ARABIC_TATWEEL.toString(), "")
            .replace(DIACRITICS_AND_BIDI_REGEX, "")
    }

    fun normalizeSpacingAndZwnj(input: String): String {
        if (input.isEmpty()) return ""
        return input
            .replace(CONSECUTIVE_ZWNJ, ZWNJ.toString())
            .replace(ZWNJ_WITH_SPACE, " ")
            .replace(MULTI_SPACE, " ")
            .trim { it <= ' ' || it == ZWNJ }
    }

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val step1 = normalizeDigits(input)
        val step2 = normalizeCharacters(step1)
        val step3 = stripDiacritics(step2)
        return normalizeSpacingAndZwnj(step3)
    }
}
