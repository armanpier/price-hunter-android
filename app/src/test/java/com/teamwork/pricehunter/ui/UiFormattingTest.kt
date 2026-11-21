package com.teamwork.pricehunter.ui

import com.teamwork.pricehunter.ui.food.formatFoodToman
import com.teamwork.pricehunter.ui.shopping.formatShoppingPrice
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFormattingTest {

    @Test
    fun testFormatShoppingPriceAppendsToman() {
        val formatted = formatShoppingPrice(1500000L)
        assertTrue("Formatted price must end with تومان", formatted.endsWith("تومان"))
        assertTrue("Formatted price must contain digits", formatted.contains("۱") || formatted.contains("1"))
    }

    @Test
    fun testFormatFoodTomanAppendsToman() {
        val formatted = formatFoodToman(85000L)
        assertTrue("Formatted food cost must end with تومان", formatted.endsWith("تومان"))
        assertTrue("Formatted food cost must contain digits", formatted.contains("۸") || formatted.contains("8"))
    }

    @Test
    fun testZeroAmountFormatting() {
        val formatted = formatShoppingPrice(0L)
        assertTrue(formatted.contains("تومان"))
    }
}
