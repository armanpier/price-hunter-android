package com.teamwork.pricehunter.core.intelligence

import org.junit.Assert.*
import org.junit.Test

class UnitExtractorTest {

    @Test
    fun test_single_gram() {
        val result = UnitExtractor.extract("تن ماهی ۱۸۰ گرمی شیلتون", 90000L)
        assertNotNull(result)
        assertEquals(180.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(500000L, result.unitPriceToman)
        assertTrue(result.displayText.contains("180") && result.displayText.contains("گرمی"))
    }

    @Test
    fun test_single_kg() {
        val result = UnitExtractor.extract("برنج هاشمی ۵ کیلوگرم", 600000L)
        assertNotNull(result)
        assertEquals(5000.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(120000L, result.unitPriceToman)
        assertEquals("5 کیلوگرم", result.displayText)
    }

    @Test
    fun test_single_ml() {
        val result = UnitExtractor.extract("شیر کم چرب ۲۰۰ میلی‌لیتر", 15000L)
        assertNotNull(result)
        assertEquals(200.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(75000L, result.unitPriceToman)
        assertTrue(result.displayText.contains("200") && result.displayText.contains("میلی‌لیتر"))
    }

    @Test
    fun test_single_liter() {
        val result = UnitExtractor.extract("روغن مایع ۱.۵ لیتری لادن", 105000L)
        assertNotNull(result)
        assertEquals(1500.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(70000L, result.unitPriceToman)
        assertEquals("1.5 لیتری", result.displayText)
    }

    @Test
    fun test_compound_vol() {
        val result = UnitExtractor.extract("شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی", 60000L)
        assertNotNull(result)
        assertEquals(1200.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(6, result.packCount)
        assertEquals(50000L, result.unitPriceToman)
        assertTrue(result.displayText.contains("1.2") && result.displayText.contains("لیتر") && result.displayText.contains("6"))
    }

    @Test
    fun test_compound_weight() {
        val result = UnitExtractor.extract("کنسرو لوبیا چیتی ۴۰۰ گرم بسته ۴ عددی", 120000L)
        assertNotNull(result)
        assertEquals(1600.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertEquals(4, result.packCount)
        assertEquals(75000L, result.unitPriceToman)
        assertTrue(result.displayText.contains("1.6") && result.displayText.contains("کیلوگرم") && result.displayText.contains("4"))
    }

    @Test
    fun test_inline_multi_vol() {
        val result = UnitExtractor.extract("شیر کاکائو میهن 6x200ml", 72000L)
        assertNotNull(result)
        assertEquals(1200.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(6, result.packCount)
        assertEquals(60000L, result.unitPriceToman)
        assertTrue(result.displayText.contains("1.2") && result.displayText.contains("لیتر"))
    }

    @Test
    fun test_piece_count_only() {
        val result = UnitExtractor.extract("دستمال کاغذی پاپیا بسته ۱۰ عددی", 250000L)
        assertNotNull(result)
        assertEquals(10.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.PIECE, result.standardUnit)
        assertEquals(10, result.packCount)
        assertEquals(25000L, result.unitPriceToman)
        assertEquals("10 عدد", result.displayText)
    }

    @Test
    fun test_fraction_slash() {
        val result = UnitExtractor.extract("شیر ۱/۲ کیلوگرم", 60000L)
        assertNotNull(result)
        assertEquals(500.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(120000L, result.unitPriceToman)
        assertEquals("0.5 کیلوگرم", result.displayText)
    }

    @Test
    fun test_decimal_comma() {
        val result1 = UnitExtractor.extract("شیر ۲,۵ لیتر", 50000L)
        assertNotNull(result1)
        assertEquals(2500.0, result1!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result1.standardUnit)

        val result2 = UnitExtractor.extract("شیر ۲،۵ لیتر", 50000L)
        assertNotNull(result2)
        assertEquals(2500.0, result2!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result2.standardUnit)
    }

    @Test
    fun test_cc_unit() {
        val result = UnitExtractor.extract("شربت سرفه ۱۲۰ سی سی", 36000L)
        assertNotNull(result)
        assertEquals(120.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, result.standardUnit)
        assertEquals(1, result.packCount)
        assertEquals(300000L, result.unitPriceToman)
    }

    @Test
    fun test_no_price() {
        val result = UnitExtractor.extract("ماست سون ۲ کیلوگرم", null)
        assertNotNull(result)
        assertEquals(2000.0, result!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, result.standardUnit)
        assertEquals(1, result.packCount)
        assertNull(result.unitPriceToman)
        assertEquals("2 کیلوگرم", result.displayText)
    }

    @Test
    fun test_non_unit_item() {
        val result = UnitExtractor.extract("گوشی موبایل سامسونگ گلکسی A54", 15000000L)
        assertNull(result)
    }

    @Test
    fun test_zero_price_returns_null_unit_price() {
        val single = UnitExtractor.extract("روغن ۱ لیتری", 0L)
        assertNotNull(single)
        assertNull(single?.unitPriceToman)

        val compound = UnitExtractor.extract("شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی", 0L)
        assertNotNull(compound)
        assertNull(compound?.unitPriceToman)

        val countOnly = UnitExtractor.extract("دستمال کاغذی بسته ۱۰ عددی", 0L)
        assertNotNull(countOnly)
        assertNull(countOnly?.unitPriceToman)
    }

    @Test
    fun test_negative_price_returns_null_unit_price() {
        val single = UnitExtractor.extract("روغن ۱ لیتری", -50000L)
        assertNotNull(single)
        assertNull(single?.unitPriceToman)

        val compound = UnitExtractor.extract("شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی", -60000L)
        assertNotNull(compound)
        assertNull(compound?.unitPriceToman)

        val countOnly = UnitExtractor.extract("دستمال کاغذی بسته ۱۰ عددی", -25000L)
        assertNotNull(countOnly)
        assertNull(countOnly?.unitPriceToman)
    }
}
