package com.teamwork.pricehunter.core.intelligence

import org.junit.Assert.*
import org.junit.Test

class FoodVendorMatcherTest {

    @Test
    fun test_exact_vendor() {
        val loc = GeoLocation(35.7500, 51.4100)
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "رستوران نایب", loc,
            "رستوران نایب", loc
        )
        assertTrue(isMatch)
        assertNotNull(pair)
        assertEquals(0.0, pair!!.distanceMeters, 0.001)
        assertEquals(1.0, pair.tokenSimilarity, 0.001)
    }

    @Test
    fun test_stop_word_strip() {
        val loc1 = GeoLocation(35.7800, 51.3700)
        val loc2 = GeoLocation(35.7801, 51.3701)
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "کافه رستوران شاندیز صبا (شعبه سعادت آباد)", loc1,
            "شاندیز صبا سعادت آباد", loc2
        )
        assertTrue(isMatch)
        assertNotNull(pair)
        assertTrue(pair!!.distanceMeters < 150.0)
        assertEquals(1.0, pair.tokenSimilarity, 0.001)
    }

    @Test
    fun test_distance_under_150m() {
        val loc1 = GeoLocation(35.7000, 51.4000)
        val loc2 = GeoLocation(35.7008, 51.4005)
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "پیتزا پردیس", loc1,
            "فست فود پردیس", loc2
        )
        assertTrue(isMatch)
        assertNotNull(pair)
        assertTrue(pair!!.distanceMeters < 150.0)
        assertTrue(pair.tokenSimilarity >= 0.80)
    }

    @Test
    fun test_distance_exceeded() {
        val loc1 = GeoLocation(35.7200, 51.4100)
        val loc2 = GeoLocation(35.7230, 51.4100) // ~333m away
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "رستوران البرز", loc1,
            "رستوران البرز", loc2
        )
        assertFalse(isMatch)
        assertNull(pair)
    }

    @Test
    fun test_different_restaurants() {
        val loc1 = GeoLocation(35.7500, 51.4100)
        val loc2 = GeoLocation(35.7501, 51.4101) // close in distance
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "رستوران جوان", loc1,
            "رستوران ارکیده", loc2
        )
        assertFalse(isMatch)
        assertNull(pair)
    }

    @Test
    fun test_typo_levenshtein() {
        val loc1 = GeoLocation(35.7300, 51.3800)
        val loc2 = GeoLocation(35.7302, 51.3801)
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "کباب بناب تبریز", loc1,
            "کباب بناب توریز", loc2
        )
        assertTrue(isMatch)
        assertNotNull(pair)
        assertTrue(pair!!.distanceMeters < 150.0)
        assertTrue(pair.tokenSimilarity >= 0.80)
    }

    @Test
    fun test_chain_diff_branches() {
        val loc1 = GeoLocation(35.7200, 51.4100) // Motahari
        val loc2 = GeoLocation(35.8050, 51.4250) // Tajrish (>9km away)
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            "پیتزا شیلا شعبه مطهری", loc1,
            "پیتزا شیلا شعبه تجریش", loc2
        )
        assertFalse(isMatch)
        assertNull(pair)
    }

    @Test
    fun test_zwnj_token_split_and_stop_words() {
        val tokensZwnj = FoodVendorMatcher.cleanVendorTokens("پیتزا پرپروک سعادت‌آباد")
        val tokensSpace = FoodVendorMatcher.cleanVendorTokens("پیتزا پرپروک سعادت آباد")
        assertEquals(tokensSpace, tokensZwnj)
        assertTrue(tokensZwnj.contains("سعادت"))
        assertTrue(tokensZwnj.contains("اباد"))
        assertTrue(tokensZwnj.contains("پرپروک"))

        val similarity = FoodVendorMatcher.computeFuzzySimilarity(
            "پیتزا پرپروک سعادت آباد",
            "فست‌فود پرپروک (سعادت‌آباد)"
        )
        assertTrue("Similarity was $similarity", similarity >= 0.80)
    }

    @Test
    fun test_normalized_vendor_stop_words() {
        val tokensAshpaz = FoodVendorMatcher.cleanVendorTokens("آشپزخانه نایب")
        assertEquals(setOf("نایب"), tokensAshpaz)

        val tokensAshpazNorm = FoodVendorMatcher.cleanVendorTokens("اشپزخانه نایب")
        assertEquals(setOf("نایب"), tokensAshpazNorm)

        val tokensAbmiveh = FoodVendorMatcher.cleanVendorTokens("آبمیوه توچال")
        assertEquals(setOf("توچال"), tokensAbmiveh)

        val tokensAbmivehNorm = FoodVendorMatcher.cleanVendorTokens("ابمیوه توچال")
        assertEquals(setOf("توچال"), tokensAbmivehNorm)

        val tokensGhazaye = FoodVendorMatcher.cleanVendorTokens("تهیه غذای فارسی")
        assertEquals(setOf("فارسی"), tokensGhazaye)

        assertTrue(FoodVendorMatcher.VENDOR_STOP_WORDS.contains("آشپزخانه"))
        assertTrue(FoodVendorMatcher.VENDOR_STOP_WORDS.contains("اشپزخانه"))
        assertTrue(FoodVendorMatcher.VENDOR_STOP_WORDS.contains("آبمیوه"))
        assertTrue(FoodVendorMatcher.VENDOR_STOP_WORDS.contains("ابمیوه"))
        assertTrue(FoodVendorMatcher.VENDOR_STOP_WORDS.contains("غذای"))
    }
}
