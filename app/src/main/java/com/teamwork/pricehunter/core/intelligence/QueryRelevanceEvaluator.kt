package com.teamwork.pricehunter.core.intelligence

object QueryRelevanceEvaluator {

    private val PERSIAN_STOP_WORDS = setOf(
        "و", "یا", "در", "به", "از", "با", "برای", "یک", "این", "آن", "ها", "های",
        "تا", "رو", "را", "که", "مدل", "برند", "خرید", "قیمت", "اصل", "اورجینال",
        "فروش", "فروشگاه", "بهترین", "ارزان", "ارزانترین"
    )

    /**
     * Determines whether [itemTitle] is sufficiently relevant to [userQuery].
     * Discards completely unrelated items (e.g. vitamins or gold coins when searching for air conditioners).
     */
    fun isRelevant(itemTitle: String, userQuery: String): Boolean {
        val normQuery = PersianNormalizer.normalize(userQuery).lowercase().trim()
        val normTitle = PersianNormalizer.normalize(itemTitle).lowercase().trim()
        if (normQuery.isBlank() || normTitle.isBlank()) return true

        // Direct containment: title contains exact query string
        if (normTitle.contains(normQuery)) return true

        val queryTokens = tokenize(normQuery)
            .filter { !PERSIAN_STOP_WORDS.contains(it) && it.length > 1 }

        if (queryTokens.isEmpty()) {
            val rawTokens = tokenize(normQuery).filter { it.isNotBlank() }
            if (rawTokens.isEmpty()) return true
            return rawTokens.any { normTitle.contains(it) }
        }

        val titleTokens = tokenize(normTitle)
            .filter { !PERSIAN_STOP_WORDS.contains(it) && it.length > 1 }

        // Count matching tokens (exact match or word-stem prefix match for tokens length >= 4)
        val matchCount = queryTokens.count { qToken ->
            titleTokens.contains(qToken) || titleTokens.any { tToken ->
                if (tToken.length >= 4 && qToken.length >= 4) {
                    tToken.startsWith(qToken) || qToken.startsWith(tToken)
                } else {
                    false
                }
            }
        }

        return when {
            queryTokens.size == 1 -> matchCount >= 1
            queryTokens.size == 2 -> matchCount >= 1
            queryTokens.size in 3..4 -> matchCount >= 2
            else -> (matchCount.toDouble() / queryTokens.size.toDouble()) >= 0.40
        }
    }

    private fun tokenize(text: String): Set<String> {
        return text.split(" ", "\t", "-", "_", "،", ",", "(", ")", "[", "]", "{", "}", "/", "\u200C", ".", ":", "«", "»", "\"", "'")
            .map { it.trim().trim('\u200C') }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
