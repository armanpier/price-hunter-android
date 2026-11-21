package com.teamwork.pricehunter.e2e.tier1_coverage

import com.teamwork.pricehunter.core.intelligence.FoodVendorMatcher
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 1: Feature Coverage — FoodVendorMatcher (5 tests)
 * Spec reference: TC-T1-FM-01 through TC-T1-FM-05
 */
class Tier1VendorMatcherTest {

    @Test
    fun TC_T1_FM_01_haversineDistanceWithinRange() {
        val loc1 = GeoLocation(35.77330, 51.41850)
        val loc2 = GeoLocation(35.77380, 51.41920)

        val distance = FoodVendorMatcher.haversineDistanceMeters(loc1, loc2)

        assertTrue("Distance should be < 150m, was $distance", distance < 150.0)
        assertTrue("Distance should be > 50m, was $distance", distance > 50.0)
    }

    @Test
    fun TC_T1_FM_02_haversineDistanceOutOfRange() {
        val loc1 = GeoLocation(35.77330, 51.41850)
        val loc2 = GeoLocation(35.77900, 51.42500)

        val distance = FoodVendorMatcher.haversineDistanceMeters(loc1, loc2)

        assertTrue("Distance should be > 150m, was $distance", distance > 150.0)
    }

    @Test
    fun TC_T1_FM_03_stopWordsRemoval() {
        val name1 = "رستوران و فست فود نایب"
        val name2 = "تهیه غذای نایب شعبه ساعی"

        val tokens1 = FoodVendorMatcher.cleanVendorTokens(name1)
        val tokens2 = FoodVendorMatcher.cleanVendorTokens(name2)

        assertTrue("Core token نایب must be present in tokens1", tokens1.contains("نایب"))
        assertTrue("Core token نایب must be present in tokens2", tokens2.contains("نایب"))
        assertFalse("Stop word رستوران must be stripped", tokens1.contains("رستوران"))
        assertFalse("Stop word فست must be stripped", tokens1.contains("فست"))
        assertFalse("Stop word فود must be stripped", tokens1.contains("فود"))
    }

    @Test
    fun TC_T1_FM_04_fuzzyTokenSimilarityAboveThreshold() {
        val name1 = "پیتزا پرپروک سعادت آباد"
        val name2 = "فست‌فود پرپروک سعادت آباد"

        val similarity = FoodVendorMatcher.computeFuzzySimilarity(name1, name2)

        assertTrue("Similarity must be >= 0.80, was $similarity", similarity >= 0.80)
    }

    @Test
    fun TC_T1_FM_05_compositeVendorMatchSuccess() {
        val nameA = "رستوران شیلا"
        val locA = GeoLocation(35.7501, 51.4001)
        val nameB = "فست فود شیلا"
        val locB = GeoLocation(35.7505, 51.4006)

        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(nameA, locA, nameB, locB)

        assertTrue("isVendorMatch should be true", isMatch)
        assertNotNull("MatchedVendorPair should not be null", pair)
        assertEquals(nameA, pair!!.vendorA)
        assertEquals(nameB, pair.vendorB)
        assertTrue("Distance should be < 150m", pair.distanceMeters < 150.0)
        assertTrue("Token similarity should be >= 0.80", pair.tokenSimilarity >= 0.80)
    }
}
