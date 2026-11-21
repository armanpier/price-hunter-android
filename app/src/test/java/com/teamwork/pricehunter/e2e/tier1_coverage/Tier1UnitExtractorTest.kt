package com.teamwork.pricehunter.e2e.tier1_coverage

import com.teamwork.pricehunter.core.intelligence.StandardUnit
import com.teamwork.pricehunter.core.intelligence.UnitExtractor
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 1: Feature Coverage — UnitExtractor (5 tests)
 * Spec reference: TC-T1-UE-01 through TC-T1-UE-05
 */
class Tier1UnitExtractorTest {

    @Test
    fun TC_T1_UE_01_singleMetricWeightExtractionGrams() {
        val title = "روغن زیتون فرابکر ۸۰۰ گرم"
        val price = 240000L
        val result = UnitExtractor.extract(title, price)

        assertNotNull("Result should not be null", result)
        assertEquals(800.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(300000L, result.unitPriceToman) // 300,000 Toman/kg
    }

    @Test
    fun TC_T1_UE_02_singleMetricVolumeExtractionLiters() {
        val title = "شیر کم چرب ۱.۵ لیتر دامداران"
        val price = 45000L
        val result = UnitExtractor.extract(title, price)

        assertNotNull("Result should not be null", result)
        assertEquals(1500.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(30000L, result.unitPriceToman) // 30,000 Toman/L
    }

    @Test
    fun TC_T1_UE_03_compoundMultiPackMultiplicationVolume() {
        val title = "شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی"
        val price = 120000L
        val result = UnitExtractor.extract(title, price)

        assertNotNull("Result should not be null", result)
        assertEquals(1200.0, result!!.baseQuantity, 0.001) // 6 * 200ml = 1200ml
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(6, result.packCount)
        assertEquals(100000L, result.unitPriceToman) // 100,000 Toman/L
    }

    @Test
    fun TC_T1_UE_04_pieceCountExtraction() {
        val title = "تخم مرغ تازه بسته ۲۰ عددی تلاونگ"
        val price = 140000L
        val result = UnitExtractor.extract(title, price)

        assertNotNull("Result should not be null", result)
        assertEquals(20.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.PIECE, result.standardUnit)
        assertEquals(20, result.packCount)
        assertEquals(7000L, result.unitPriceToman) // 7,000 Toman/piece
    }

    @Test
    fun TC_T1_UE_05_nullPriceFallback() {
        val title = "ماست همزده دبه ای ۲.۵ کیلوگرم کاله"
        val result = UnitExtractor.extract(title, null)

        assertNotNull("Result should not be null", result)
        assertEquals(2500.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertNull("unitPriceToman must be null when price is not provided", result.unitPriceToman)
    }
}
