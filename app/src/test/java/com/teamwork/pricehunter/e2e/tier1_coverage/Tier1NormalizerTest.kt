package com.teamwork.pricehunter.e2e.tier1_coverage

import com.teamwork.pricehunter.core.intelligence.PersianNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tier 1: Feature Coverage — PersianNormalizer (6 tests)
 * Spec reference: TC-T1-PN-01 through TC-T1-PN-06
 */
class Tier1NormalizerTest {

    @Test
    fun TC_T1_PN_01_arabicCharacterNormalization() {
        val input = "كيك شكلاتى با خمير تازه و پنير موزارلا با طعم عالى"
        val expected = "کیک شکلاتی با خمیر تازه و پنیر موزارلا با طعم عالی"
        val actual = PersianNormalizer.normalizeCharacters(input)
        assertEquals(expected, actual)
    }

    @Test
    fun TC_T1_PN_02_diacriticsStripping() {
        val input = "شِیرِ پُرچَرب کَالِه بَدُونَ چَربِیّ"
        val expected = "شیر پرچرب کاله بدون چربی"
        val actual = PersianNormalizer.stripDiacritics(input)
        assertEquals(expected, actual)
    }

    @Test
    fun TC_T1_PN_03_tatweelKashidaRemoval() {
        val input = "پـیـتـزا مـخـصـوص"
        val expected = "پیتزا مخصوص"
        val actual = PersianNormalizer.normalize(input)
        assertEquals(expected, actual)
    }

    @Test
    fun TC_T1_PN_04_persianAndArabicNumeralConversion() {
        val input = "بسته ۶ عددی شیر ۲۰۰ میلی‌لیتر و کد تخفیف ٥٠ درصدی"
        val expected = "بسته 6 عددی شیر 200 میلی‌لیتر و کد تخفیف 50 درصدی"
        val actual = PersianNormalizer.normalizeDigits(input)
        assertEquals(expected, actual)
    }

    @Test
    fun TC_T1_PN_05_spacingAndZwnjNormalization() {
        val input = "  روغن   زیتون   می‌شود   و   خانه‌ها   "
        val expected = "روغن زیتون می‌شود و خانه‌ها"
        val actual = PersianNormalizer.normalizeSpacingAndZwnj(input)
        assertEquals(expected, actual)
    }

    @Test
    fun TC_T1_PN_06_compositeFullNormalizationPipeline() {
        val input = "  كيكِ   شـكلاتى   بسته   ۶   عددى  "
        val expected = "کیک شکلاتی بسته 6 عددی"
        val actual = PersianNormalizer.normalize(input)
        assertEquals(expected, actual)
    }
}
