package com.teamwork.pricehunter.e2e.tier1_coverage

import com.teamwork.pricehunter.core.intelligence.AccessoryAndOutlierFilter
import com.teamwork.pricehunter.core.intelligence.PriceableItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 1: Feature Coverage — AccessoryAndOutlierFilter (5 tests)
 * Spec reference: TC-T1-AF-01 through TC-T1-AF-05
 */
class Tier1AccessoryFilterTest {

    data class SimpleItem(override val title: String, override val priceToman: Long) : PriceableItem

    @Test
    fun TC_T1_AF_01_accessoryDetectionNonAccessoryQuery() {
        val query = "گوشی موبایل سامسونگ گلکسی A54"
        val items = listOf(
            SimpleItem("گوشی سامسونگ گلکسی A54 ظرفیت 128", 12000000L),
            SimpleItem("گوشی موبایل سامسونگ A54 5G", 12200000L),
            SimpleItem("قاب ژله ای مناسب برای گوشی سامسونگ A54", 150000L),
            SimpleItem("گلس سرامیکی سامسونگ A54", 80000L)
        )

        val result = AccessoryAndOutlierFilter.filter(items, query, 0.20)

        assertEquals(2, result.primaryResults.size)
        assertEquals(2, result.accessoryResults.size)
        assertTrue(result.primaryResults.all { !it.title.contains("قاب") && !it.title.contains("گلس") })
        assertTrue(result.accessoryResults.any { it.title.contains("قاب") })
        assertTrue(result.accessoryResults.any { it.title.contains("گلس") })
    }

    @Test
    fun TC_T1_AF_02_explicitAccessoryQueryIntent() {
        val query = "خرید قاب گوشی آیفون 13"
        val items = listOf(
            SimpleItem("قاب سیلیکونی آیفون 13", 200000L),
            SimpleItem("قاب ضد ضربه آیفون 13", 350000L)
        )

        val result = AccessoryAndOutlierFilter.filter(items, query, 0.20)

        assertEquals(2, result.primaryResults.size)
        assertEquals(0, result.accessoryResults.size)
    }

    @Test
    fun TC_T1_AF_03_oddCountMedianCalculation() {
        val prices = listOf(100000L, 250000L, 500000L, 800000L, 1200000L)
        val median = AccessoryAndOutlierFilter.calculateMedian(prices)
        assertEquals(500000L, median)
    }

    @Test
    fun TC_T1_AF_04_evenCountMedianCalculation() {
        val prices = listOf(200000L, 400000L, 600000L, 800000L)
        val median = AccessoryAndOutlierFilter.calculateMedian(prices)
        assertEquals(500000L, median)
    }

    @Test
    fun TC_T1_AF_05_lowPriceOutlierFlagging() {
        val query = "گوشی پوکو X5"
        val items = listOf(
            SimpleItem("گوشی پوکو X5 پرو 256", 10000000L),
            SimpleItem("گوشی شیائومی پوکو X5", 10500000L),
            SimpleItem("گوشی موبایل پوکو X5 فایوجی", 9800000L),
            SimpleItem("ماکت یا طرح گوشی پوکو X5", 1500000L) // Outlier < 20% of ~10M
        )

        val result = AccessoryAndOutlierFilter.filter(items, query, 0.20)

        assertEquals(3, result.primaryResults.size)
        assertEquals(1, result.suspiciousOutliers.size)
        assertEquals(1500000L, result.suspiciousOutliers[0].priceToman)
    }
}
