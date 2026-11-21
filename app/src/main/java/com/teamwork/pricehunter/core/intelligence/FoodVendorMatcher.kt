package com.teamwork.pricehunter.core.intelligence

import kotlin.math.*

data class GeoLocation(val latitude: Double, val longitude: Double)

data class MatchedVendorPair<T>(
    val vendorA: T,
    val vendorB: T,
    val distanceMeters: Double,
    val tokenSimilarity: Double
)

object FoodVendorMatcher {
    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val MAX_DISTANCE_METERS = 150.0
    private const val MIN_SIMILARITY_SCORE = 0.80

    private val BASE_VENDOR_STOP_WORDS = listOf(
        "رستوران", "کافه", "فست", "فود", "فست‌فود", "فستفود", "تهیه", "غذا", "غذای", "کترینگ", "کیترینگ",
        "پیتزا", "ساندویچ", "ساندویچی", "شعبه", "مجموعه", "غذایی", "آشپزخانه", "اشپزخانه", "باربیکیو",
        "گریل", "برگر", "چلوکباب", "چلوکبابی", "کبابی", "کباب", "خانگی", "طباخی", "دیزی",
        "آبمیوه", "ابمیوه", "بستنی", "سوخاری", "دریایی", "بریانی", "اصلی", "مرکزی", "و", "با"
    )

    val VENDOR_STOP_WORDS: Set<String> = buildSet {
        for (w in BASE_VENDOR_STOP_WORDS) {
            add(w)
            val normalized = PersianNormalizer.normalize(w)
            if (normalized.isNotBlank()) {
                add(normalized)
            }
        }
    }

    fun haversineDistanceMeters(loc1: GeoLocation, loc2: GeoLocation): Double {
        val lat1Rad = Math.toRadians(loc1.latitude)
        val lat2Rad = Math.toRadians(loc2.latitude)
        val deltaLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val deltaLon = Math.toRadians(loc2.longitude - loc1.longitude)

        val sinDeltaLat = sin(deltaLat / 2)
        val sinDeltaLon = sin(deltaLon / 2)

        val a = sinDeltaLat * sinDeltaLat +
                cos(lat1Rad) * cos(lat2Rad) * sinDeltaLon * sinDeltaLon
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    fun cleanVendorTokens(name: String): Set<String> {
        val normalized = PersianNormalizer.normalize(name)
        return normalized
            .replace('\u200C', ' ')
            .replace(Regex("[\\(\\)\\[\\]\\-_/،,]"), " ")
            .split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.length > 1 && !VENDOR_STOP_WORDS.contains(it) }
            .toSet()
    }

    fun jaccardSimilarity(tokens1: Set<String>, tokens2: Set<String>): Double {
        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    fun levenshteinSimilarity(s1: String, s2: String): Double {
        val m = s1.length
        val n = s2.length
        if (m == 0 && n == 0) return 1.0
        if (m == 0 || n == 0) return 0.0

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        val distance = dp[m][n]
        val maxLen = max(m, n)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    fun computeFuzzySimilarity(name1: String, name2: String): Double {
        val tokens1 = cleanVendorTokens(name1)
        val tokens2 = cleanVendorTokens(name2)
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            val s1 = PersianNormalizer.normalize(name1)
            val s2 = PersianNormalizer.normalize(name2)
            return levenshteinSimilarity(s1, s2)
        }
        val jaccard = jaccardSimilarity(tokens1, tokens2)

        val cleanStr1 = tokens1.sorted().joinToString(" ")
        val cleanStr2 = tokens2.sorted().joinToString(" ")
        val levenshtein = levenshteinSimilarity(cleanStr1, cleanStr2)

        return max(jaccard, levenshtein)
    }

    fun isVendorMatch(
        name1: String, loc1: GeoLocation,
        name2: String, loc2: GeoLocation
    ): Pair<Boolean, MatchedVendorPair<String>?> {
        val distance = haversineDistanceMeters(loc1, loc2)
        if (distance > MAX_DISTANCE_METERS) {
            return Pair(false, null)
        }

        val similarity = computeFuzzySimilarity(name1, name2)
        val isMatch = similarity >= MIN_SIMILARITY_SCORE

        val pair = if (isMatch) MatchedVendorPair(name1, name2, distance, similarity) else null
        return Pair(isMatch, pair)
    }
}
