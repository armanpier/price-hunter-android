package com.teamwork.pricehunter.core.intelligence

import org.junit.Assert.*
import org.junit.Test

class AccessoryAndOutlierFilterTest {

    data class TestProduct(
        override val title: String,
        override val priceToman: Long
    ) : PriceableItem

    @Test
    fun test_explicit_query_intent() {
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("قاب آیفون 13"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("خرید شارژر سامسونگ"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("گلس گوشی شیائومی"))
    }

    @Test
    fun test_non_accessory_query() {
        assertFalse(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("گوشی سامسونگ S23"))
        assertFalse(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("لپ تاپ ایسوس"))
        assertFalse(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("تلویزیون سونی"))
    }

    @Test
    fun test_accessory_item_case() {
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("کاور سیلیکونی آیفون 13"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("قاب ژله ای شفاف سامسونگ"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("بک کاور ضد ضربه شیائومی"))
    }

    @Test
    fun test_accessory_item_glass() {
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("گلس فول چسب صفحه نمایش"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("محافظ لنز دوربین آیفون"))
    }

    @Test
    fun test_false_positive_shield() {
        assertFalse(AccessoryAndOutlierFilter.isItemAccessory("گوشی سامسونگ با قابلیت شارژ سریع"))
        assertFalse(AccessoryAndOutlierFilter.isItemAccessory("لباسشویی اسنوا مدل واشبند"))
        assertFalse(AccessoryAndOutlierFilter.isItemAccessory("روغن پدیده با کیفیت عالی"))
    }

    @Test
    fun test_median_odd_count() {
        val prices = listOf(100L, 300L, 200L)
        assertEquals(200L, AccessoryAndOutlierFilter.calculateMedian(prices))
    }

    @Test
    fun test_median_even_count() {
        val prices = listOf(100L, 400L, 200L, 300L)
        assertEquals(250L, AccessoryAndOutlierFilter.calculateMedian(prices))
    }

    @Test
    fun test_median_empty() {
        assertEquals(0L, AccessoryAndOutlierFilter.calculateMedian(emptyList()))
    }

    @Test
    fun test_outlier_flagging() {
        val items = listOf(
            TestProduct("گوشی موبایل A", 10000000L),
            TestProduct("گوشی موبایل B", 9500000L),
            TestProduct("گوشی موبایل C", 10500000L),
            TestProduct("گوشی موبایل تقلبی مشکوک", 1500000L) // 15% of median -> outlier
        )
        val filtered = AccessoryAndOutlierFilter.filter(items, "گوشی موبایل")
        assertEquals(3, filtered.primaryResults.size)
        assertEquals(1, filtered.suspiciousOutliers.size)
        assertEquals("گوشی موبایل تقلبی مشکوک", filtered.suspiciousOutliers[0].title)
    }

    @Test
    fun test_non_outlier_low() {
        val items = listOf(
            TestProduct("گوشی موبایل A", 10000000L),
            TestProduct("گوشی موبایل B", 10000000L),
            TestProduct("گوشی موبایل با تخفیف ویژه", 2500000L) // 25% of median >= 20% -> primary
        )
        val filtered = AccessoryAndOutlierFilter.filter(items, "گوشی موبایل")
        assertEquals(3, filtered.primaryResults.size)
        assertEquals(0, filtered.suspiciousOutliers.size)
    }

    @Test
    fun test_accessory_separation() {
        val items = listOf(
            TestProduct("گوشی موبایل آیفون 13", 40000000L),
            TestProduct("کاور سیلیکونی آیفون 13", 200000L),
            TestProduct("گلس محافظ صفحه آیفون 13", 100000L)
        )
        val filtered = AccessoryAndOutlierFilter.filter(items, "آیفون 13")
        assertEquals(1, filtered.primaryResults.size)
        assertEquals("گوشی موبایل آیفون 13", filtered.primaryResults[0].title)
        assertEquals(2, filtered.accessoryResults.size)
    }

    @Test
    fun test_accessory_no_demote() {
        val items = listOf(
            TestProduct("قاب سیلیکونی آیفون 13 مدل A", 200000L),
            TestProduct("قاب چرمی آیفون 13 مدل B", 350000L),
            TestProduct("کاور شفاف آیفون 13", 150000L)
        )
        val filtered = AccessoryAndOutlierFilter.filter(items, "قاب آیفون 13")
        // User explicitly searched for "قاب", so accessories must NOT be demoted
        assertEquals(3, filtered.primaryResults.size)
        assertEquals(0, filtered.accessoryResults.size)
    }

    @Test
    fun test_adapter_keyword_recognition() {
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("آداپتور 20 وات اپل"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("اداپتور 20 وات اپل"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("خرید آداپتور آیفون"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("خرید اداپتور آیفون"))
        assertTrue(AccessoryAndOutlierFilter.ACCESSORY_KEYWORDS.contains("آداپتور"))
        assertTrue(AccessoryAndOutlierFilter.ACCESSORY_KEYWORDS.contains("اداپتور"))
    }

    @Test
    fun test_cooling_pad_zwnj_and_space() {
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("پایه خنک کننده دیپ کول"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("پایه خنک‌کننده دیپ کول"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("پایه خنک کننده لپ تاپ"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("پایه خنک‌کننده لپ تاپ"))
        assertTrue(AccessoryAndOutlierFilter.ACCESSORY_KEYWORDS.contains("پایه خنک کننده"))
        assertTrue(AccessoryAndOutlierFilter.ACCESSORY_KEYWORDS.contains("پایه خنک‌کننده"))
    }

    @Test
    fun test_zwnj_compound_accessory_recognition() {
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("قاب\u200Cگوشی آیفون ۱۵"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("قاب\u200Cآیفون پرومکس"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("کاور\u200Cسیلیکونی سامسونگ"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("گلس\u200Cسرامیکی مات"))
        assertTrue(AccessoryAndOutlierFilter.isItemAccessory("شارژر\u200Cسریع شیائومی"))

        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("قاب\u200Cگوشی آیفون ۱۵"))
        assertTrue(AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("کاور\u200Cسیلیکونی سامسونگ"))
    }

    @Test
    fun test_median_extreme_prices_overflow_safety() {
        val extremePrices = listOf(Long.MAX_VALUE - 100L, Long.MAX_VALUE - 10L)
        val median = AccessoryAndOutlierFilter.calculateMedian(extremePrices)
        assertEquals(Long.MAX_VALUE - 55L, median)
        assertTrue(median > 0L)
    }
}
