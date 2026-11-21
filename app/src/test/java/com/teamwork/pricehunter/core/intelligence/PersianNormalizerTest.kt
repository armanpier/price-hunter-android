package com.teamwork.pricehunter.core.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianNormalizerTest {

    @Test
    fun test_arabic_yeh() {
        val input = "گوشي موبايل سامسونگ"
        val expected = "گوشی موبایل سامسونگ"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_alef_maksura() {
        val input = "حتى"
        val expected = "حتی"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_arabic_kaf() {
        val input = "كتاب آموزشى"
        val expected = "کتاب اموزشی"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_teh_marbuta() {
        val input = "جامعة تهران"
        val expected = "جامعه تهران"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_heh_yeh() {
        val input = "خانۀ من"
        val expected = "خانه من"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_alef_variants() {
        val input = "آیفون أبل إیرپاد ٱنلاین"
        val expected = "ایفون ابل ایرپاد انلاین"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_diacritics() {
        val input = "کِتَابٌ مَفْتُوحٌ"
        val expected = "کتاب مفتوح"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_shadda() {
        val input = "فرّاش محّله"
        val expected = "فراش محله"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_tatweel() {
        val input = "ســــامســــونگ"
        val expected = "سامسونگ"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_persian_digits() {
        val input = "قیمت: ۱۲۵۰۰۰ تومان"
        val expected = "قیمت: 125000 تومان"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_arabic_digits() {
        val input = "السعر: ٤٥٠٠٠"
        val expected = "السعر: 45000"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_arabic_decimal() {
        val input = "وزن: ۱٫۵ کیلو"
        val expected = "وزن: 1.5 کیلو"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_multiple_zwnj() {
        val input = "میلی\u200C\u200C\u200Cلیتر"
        val expected = "میلی\u200Cلیتر"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_spaced_zwnj() {
        val input = "میلی \u200C لیتر"
        val expected = "میلی لیتر"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_trim_zwnj() {
        val input = "\u200Cسامسونگ\u200C"
        val expected = "سامسونگ"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_multi_spaces() {
        val input = "گوشی    سامسونگ   A54"
        val expected = "گوشی سامسونگ A54"
        assertEquals(expected, PersianNormalizer.normalize(input))
    }

    @Test
    fun test_empty_blank() {
        assertEquals("", PersianNormalizer.normalize(""))
        assertEquals("", PersianNormalizer.normalize("   \u200C  "))
    }

    @Test
    fun test_alef_madda_normalization() {
        assertEquals("اداپتور", PersianNormalizer.normalize("آداپتور"))
        assertEquals("اشپزخانه", PersianNormalizer.normalize("آشپزخانه"))
        assertEquals("ابمیوه", PersianNormalizer.normalize("آبمیوه"))
    }

    @Test
    fun test_bidi_controls_stripped() {
        // LRM (\u200E) and RLM (\u200F)
        val input = "\u200Fرستوران\u200E شاندیز"
        assertEquals("رستوران شاندیز", PersianNormalizer.normalize(input))
        assertEquals("رستوران شاندیز", PersianNormalizer.stripDiacritics(input))

        // Embedding controls \u202A through \u202E
        val embedding = "\u202Aکافه\u202C \u202Bویونا\u202C"
        assertEquals("کافه ویونا", PersianNormalizer.normalize(embedding))
    }
}
