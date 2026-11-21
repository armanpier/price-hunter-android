package com.teamwork.pricehunter.e2e.tier2_boundary

import com.teamwork.pricehunter.core.intelligence.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 2: Boundary & Corner Cases — Offline Intelligence Core (20 tests)
 * Spec reference: TC-T2-PN-01..05, TC-T2-UE-01..05, TC-T2-AF-01..05, TC-T2-FM-01..05
 */
class Tier2IntelligenceBoundaryTest {

    data class TestItem(override val title: String, override val priceToman: Long) : PriceableItem

    // --- 1. PersianNormalizer Boundaries (5 tests) ---

    @Test
    fun TC_T2_PN_01_emptyAndWhitespaceStrings() {
        assertEquals("", PersianNormalizer.normalize(""))
        assertEquals("", PersianNormalizer.normalize("   "))
        assertEquals("", PersianNormalizer.normalize("\t\n\r"))
    }

    @Test
    fun TC_T2_PN_02_pureDiacriticsAndKashidaString() {
        val input = "َُِّـًٌٍْ"
        val result = PersianNormalizer.normalize(input)
        assertEquals("", result)
    }

    @Test
    fun TC_T2_PN_03_extremeStringLength() {
        val builder = java.lang.StringBuilder()
        for (i in 0 until 10000) {
            builder.append("تِست ")
        }
        val start = System.currentTimeMillis()
        val result = PersianNormalizer.normalize(builder.toString())
        val duration = System.currentTimeMillis() - start

        assertNotNull(result)
        assertTrue("Extreme string normalization should finish in <500ms", duration < 500)
    }

    @Test
    fun TC_T2_PN_04_consecutiveRedundantZwnjCharacters() {
        val input = "می‌\u200C\u200C\u200Cشود"
        val result = PersianNormalizer.normalizeSpacingAndZwnj(input)
        assertEquals("می\u200Cشود", result)
    }

    @Test
    fun TC_T2_PN_05_mixedDecimalFormatsWithArabicPersianDigits() {
        val input = "قیمت ۱۲٫۵ و ۳.۱۴ و ٥٫٩"
        val result = PersianNormalizer.normalizeDigits(input)
        assertEquals("قیمت 12.5 و 3.14 و 5.9", result)
    }

    // --- 2. UnitExtractor Boundaries (5 tests) ---

    @Test
    fun TC_T2_UE_01_numbersWithoutMetricUnits() {
        val title = "گوشی سامسونگ مدل A54 با حافظه ۲۵۶"
        val result = UnitExtractor.extract(title, 14000000L)
        assertNull("Storage numbers should not be parsed as weight/volume units", result)
    }

    @Test
    fun TC_T2_UE_02_zeroQuantityHandling() {
        val title = "بسته ۰ عددی نمونه آزمایشی"
        val result = UnitExtractor.extract(title, 0L)
        // Handled safely without division by zero
        assertTrue(result == null || result.baseQuantity == 0.0)
    }

    @Test
    fun TC_T2_UE_03_zeroAndNegativeMerchandisePrice() {
        val title = "روغن ۱ لیتری"
        val resultZero = UnitExtractor.extract(title, 0L)
        assertNotNull(resultZero)
        assertNull("Unit price should be null for zero price", resultZero?.unitPriceToman)

        val resultNegative = UnitExtractor.extract(title, -50000L)
        assertNotNull(resultNegative)
        assertNull("Unit price should be null for negative price", resultNegative?.unitPriceToman)
    }

    @Test
    fun TC_T2_UE_04_unsupportedOrNonStandardUnits() {
        val title = "وزنه ورزشی ۵ پوندی"
        val result = UnitExtractor.extract(title, 50000L)
        assertNull("Non-standard units (pounds) should return null", result)
    }

    @Test
    fun TC_T2_UE_05_fractionalMeasurements() {
        val title = "روغن زیتون ۰.۵ لیتری"
        val result = UnitExtractor.extract(title, 60000L)

        assertNotNull(result)
        assertEquals(500.0, result!!.baseQuantity, 0.01) // 0.5 L = 500 ml
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(120000L, result.unitPriceToman) // 120,000 Toman/L
    }

    // --- 3. AccessoryAndOutlierFilter Boundaries (5 tests) ---

    @Test
    fun TC_T2_AF_01_emptyAndSingleItemCollections() {
        val emptyResult = AccessoryAndOutlierFilter.filter(emptyList<TestItem>(), "گوشی")
        assertEquals(0, emptyResult.primaryResults.size)
        assertEquals(0L, emptyResult.medianPriceToman)

        val singleResult = AccessoryAndOutlierFilter.filter(
            listOf(TestItem("گوشی موبایل", 10000000L)),
            "گوشی"
        )
        assertEquals(1, singleResult.primaryResults.size)
        assertEquals(10000000L, singleResult.medianPriceToman)
        assertEquals(0, singleResult.suspiciousOutliers.size)
    }

    @Test
    fun TC_T2_AF_02_zeroPriceSpreadAllIdenticalPrice() {
        val items = (1..5).map { TestItem("گوشی سامسونگ", 5000000L) }
        val result = AccessoryAndOutlierFilter.filter(items, "گوشی")

        assertEquals(5000000L, result.medianPriceToman)
        assertEquals(5, result.primaryResults.size)
        assertEquals(0, result.suspiciousOutliers.size)
    }

    @Test
    fun TC_T2_AF_03_exact20PercentThresholdBoundary() {
        val items = listOf(
            TestItem("گوشی A", 10000000L),
            TestItem("گوشی B", 10000000L),
            TestItem("گوشی C", 10000000L),
            TestItem("گوشی D (20%)", 2000000L),      // Exactly 20%
            TestItem("گوشی E (19.9%)", 1999999L)    // Below 20%
        )

        val result = AccessoryAndOutlierFilter.filter(items, "گوشی", 0.20)

        assertTrue("Item with exactly 20% must be retained in primary", result.primaryResults.any { it.title.contains("20%") })
        assertTrue("Item with < 20% must be flagged in outliers", result.suspiciousOutliers.any { it.title.contains("19.9%") })
    }

    @Test
    fun TC_T2_AF_04_accessoryKeywordAsSubstringOfLegitimateProduct() {
        val title = "کتاب تاریخ قابوس‌نامه"
        val isAccessory = AccessoryAndOutlierFilter.isItemAccessory(title)
        assertFalse("قابوس‌نامه should not match accessory keyword قاب due to token boundary", isAccessory)
    }

    @Test
    fun TC_T2_AF_05_extremeOutlierZeroPriceItem() {
        val items = listOf(
            TestItem("گوشی A", 10000000L),
            TestItem("گوشی B", 10000000L),
            TestItem("هدیه ۰ تومانی", 0L)
        )

        val result = AccessoryAndOutlierFilter.filter(items, "گوشی")
        // Zero price is flagged as outlier (< 20% of 10M = 2M)
        assertTrue(result.suspiciousOutliers.any { it.priceToman == 0L })
    }

    // --- 4. FoodVendorMatcher Boundaries (5 tests) ---

    @Test
    fun TC_T2_FM_01_zeroDistanceBoundaryIdenticalCoordinates() {
        val loc = GeoLocation(35.7000, 51.4000)
        val distance = FoodVendorMatcher.haversineDistanceMeters(loc, loc)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun TC_T2_FM_02_exact150mDistanceThreshold() {
        val loc1 = GeoLocation(35.77330, 51.41850)
        // Offset latitude by ~0.00135 degrees is ~150 meters
        val locClose = GeoLocation(35.77430, 51.41850) // ~111m
        val locFar = GeoLocation(35.77550, 51.41850)   // ~244m

        val distClose = FoodVendorMatcher.haversineDistanceMeters(loc1, locClose)
        val distFar = FoodVendorMatcher.haversineDistanceMeters(loc1, locFar)

        assertTrue(distClose < 150.0)
        assertTrue(distFar > 150.0)
    }

    @Test
    fun TC_T2_FM_03_globalBoundaryCoordinates() {
        val southPole = GeoLocation(-90.0, 0.0)
        val northPole = GeoLocation(90.0, 0.0)

        val distance = FoodVendorMatcher.haversineDistanceMeters(southPole, northPole)
        assertTrue("Antipodal distance should be ~20,000 km", distance > 19000000.0 && distance < 21000000.0)
    }

    @Test
    fun TC_T2_FM_04_allStopWordsRestaurantName() {
        val name1 = "رستوران فست فود کترینگ"
        val name2 = "کافه تهیه غذا"

        val similarity = FoodVendorMatcher.computeFuzzySimilarity(name1, name2)
        // All stop words stripped, computes cleanly without divide-by-zero
        assertTrue(similarity <= 0.80)
    }

    @Test
    fun TC_T2_FM_05_exact80PercentTokenSimilarityBoundary() {
        // High similarity vs Low similarity
        val simHigh = FoodVendorMatcher.computeFuzzySimilarity("پیتزا پرپروک", "پرپروک")
        val simLow = FoodVendorMatcher.computeFuzzySimilarity("پیتزا پرپروک", "چلوکباب نایب")

        assertTrue("Identical core token پرپروک should be >= 0.80", simHigh >= 0.80)
        assertTrue("Completely disjoint names should be < 0.80", simLow < 0.80)
    }
}
