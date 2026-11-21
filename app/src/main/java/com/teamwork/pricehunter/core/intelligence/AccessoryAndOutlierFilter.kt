package com.teamwork.pricehunter.core.intelligence

data class FilteredSearchResults<T>(
    val primaryResults: List<T>,
    val accessoryResults: List<T>,
    val suspiciousOutliers: List<T>,
    val medianPriceToman: Long
)

interface PriceableItem {
    val title: String
    val priceToman: Long
}

object AccessoryAndOutlierFilter {
    private val BASE_ACCESSORY_KEYWORDS = listOf(
        "قاب", "کاور", "گارد", "بک کاور", "بک‌کاور", "کیف", "جلد", "قاب محافظ",
        "گلس", "محافظ صفحه", "محافظ صفحه نمایش", "محافظ لنز", "محافظ پشت", "برچسب",
        "کابل", "کابل شارژ", "سیم شارژ", "شارژر", "آداپتور", "اداپتور", "کلگی", "داک شارژ",
        "پاوربانک", "پاور بانک", "محافظ کابل",
        "هولدر", "پایه نگهدارنده", "پایه نگه‌دارنده", "پایه نگه دارنده", "استند", "نگهدارنده", "نگه‌دارنده",
        "پایه خنک کننده", "پایه خنک‌کننده",
        "بند", "بند ساعت", "بند مچ بند", "بند مچ‌بند", "حلقه",
        "سرگوشی", "روکش",
        "تبدیل", "مبدل", "رابط", "او تی جی", "otg", "دانگل"
    )

    val ACCESSORY_KEYWORDS: Set<String> = buildSet {
        for (kw in BASE_ACCESSORY_KEYWORDS) {
            add(kw)
            val normalized = PersianNormalizer.normalize(kw)
            if (normalized.isNotBlank()) add(normalized)

            if (kw.contains(" ")) {
                val zwnjVariant = kw.replace(" ", "\u200C")
                add(zwnjVariant)
                val normZwnj = PersianNormalizer.normalize(zwnjVariant)
                if (normZwnj.isNotBlank()) add(normZwnj)
            }
            if (kw.contains("\u200C")) {
                val spaceVariant = kw.replace("\u200C", " ")
                add(spaceVariant)
                val normSpace = PersianNormalizer.normalize(spaceVariant)
                if (normSpace.isNotBlank()) add(normSpace)
            }
        }
    }

    fun queryExplicitlyRequestsAccessory(query: String): Boolean {
        val normalized = PersianNormalizer.normalize(query)
        if (normalized.isBlank()) return false
        val tokens = tokenize(normalized)
        return ACCESSORY_KEYWORDS.any { kw ->
            if (kw.contains(" ") || kw.contains("‌")) {
                normalized.contains(kw)
            } else {
                tokens.contains(kw)
            }
        }
    }

    fun isItemAccessory(title: String): Boolean {
        val normalized = PersianNormalizer.normalize(title)
        if (normalized.isBlank()) return false
        val tokens = tokenize(normalized)
        return ACCESSORY_KEYWORDS.any { kw ->
            if (kw.contains(" ") || kw.contains("‌")) {
                normalized.contains(kw)
            } else {
                tokens.contains(kw)
            }
        }
    }

    private fun tokenize(text: String): Set<String> {
        return text.split(" ", "\t", "-", "_", "،", ",", "(", ")", "[", "]", "{", "}", "/", "\u200C")
            .map { it.trim().trim('\u200C') }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun calculateMedian(prices: List<Long>): Long {
        if (prices.isEmpty()) return 0L
        val sorted = prices.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            sorted[n / 2 - 1] + (sorted[n / 2] - sorted[n / 2 - 1]) / 2L
        }
    }

    fun <T : PriceableItem> filter(
        items: List<T>,
        userQuery: String,
        outlierRatioThreshold: Double = 0.20
    ): FilteredSearchResults<T> {
        if (items.isEmpty()) {
            return FilteredSearchResults(emptyList(), emptyList(), emptyList(), 0L)
        }

        val userWantsAccessory = queryExplicitlyRequestsAccessory(userQuery)

        // Step 1: Partition accessories if user did not explicitly request them
        val (candidates, accessories) = if (!userWantsAccessory) {
            items.partition { !isItemAccessory(it.title) }
        } else {
            Pair(items, emptyList())
        }

        // Step 2: Compute market median price on positive prices
        val positivePrices = candidates.map { it.priceToman }.filter { it > 0 }
        val medianPrice = calculateMedian(positivePrices)
        val lowerBound = (medianPrice * outlierRatioThreshold).toLong()

        // Step 3: Flag low-price outliers (< 20% of median)
        val (primary, outliers) = candidates.partition { item ->
            medianPrice == 0L || item.priceToman >= lowerBound
        }

        return FilteredSearchResults(
            primaryResults = primary,
            accessoryResults = accessories,
            suspiciousOutliers = outliers,
            medianPriceToman = medianPrice
        )
    }
}
