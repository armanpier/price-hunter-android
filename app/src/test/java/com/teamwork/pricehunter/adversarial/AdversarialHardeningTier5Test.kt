package com.teamwork.pricehunter.adversarial

import com.teamwork.pricehunter.core.intelligence.*
import com.teamwork.pricehunter.core.network.DebugNetworkCapturePool
import com.teamwork.pricehunter.core.network.HeaderSanitizer
import com.teamwork.pricehunter.core.network.NetworkCaptureEntry
import com.teamwork.pricehunter.core.provider.DeliveredFoodCostBreakdown
import com.teamwork.pricehunter.core.provider.ProviderResult
import com.teamwork.pricehunter.core.provider.TapsiFoodProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/**
 * Milestone 4 — Tier 5 Adversarial Coverage Hardening Test Suite
 * Verifies genuine remediation of all 7 white-box adversarial edge cases identified by Challengers:
 * 1. AccessoryAndOutlierFilter ZWNJ Tokenization
 * 2. AccessoryAndOutlierFilter Median Arithmetic Overflow
 * 3. UnitExtractor ZWNJ between Digits, Units, and Multipliers
 * 4. UnitExtractor Genuine Fraction Slash and Decimal Comma Parsing
 * 5. PersianNormalizer & FoodVendorMatcher Unicode Bidirectional Control Stripping
 * 6. DebugNetworkCapturePool HeaderSanitizer Token & Auth Scrubbing
 * 7. TapsiFoodProvider Individual Dish Total Cost Calculation
 */
class AdversarialHardeningTier5Test {

    data class DummyProduct(
        override val title: String,
        override val priceToman: Long
    ) : PriceableItem

    // --- Defect 1: ZWNJ Tokenization in Accessory & Outlier Filter ---

    @Test
    fun testDefect1_accessoryZwnjTokenizationRecognition() {
        // Compound accessories joined with ZWNJ (\u200C) must be recognized as accessories
        assertTrue("قاب‌گوشی should be recognized as accessory",
            AccessoryAndOutlierFilter.isItemAccessory("قاب\u200Cگوشی آیفون ۱۵"))
        assertTrue("قاب‌آیفون should be recognized as accessory",
            AccessoryAndOutlierFilter.isItemAccessory("قاب\u200Cآیفون پرومکس"))
        assertTrue("کاور‌سیلیکونی should be recognized as accessory",
            AccessoryAndOutlierFilter.isItemAccessory("کاور\u200Cسیلیکونی سامسونگ"))
        assertTrue("گلس‌سرامیکی should be recognized as accessory",
            AccessoryAndOutlierFilter.isItemAccessory("گلس\u200Cسرامیکی مات"))
        assertTrue("شارژر‌سریع should be recognized as accessory",
            AccessoryAndOutlierFilter.isItemAccessory("شارژر\u200Cسریع شیائومی"))

        // User queries with ZWNJ must be recognized as requesting accessories
        assertTrue("User query قاب‌گوشی should be recognized as requesting accessory",
            AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("قاب\u200Cگوشی آیفون ۱۵"))
        assertTrue("User query کاور‌سیلیکونی should be recognized as requesting accessory",
            AccessoryAndOutlierFilter.queryExplicitlyRequestsAccessory("کاور\u200Cسیلیکونی سامسونگ"))

        // When user explicitly searches with ZWNJ for accessory, accessories must NOT be demoted
        val items = listOf(
            DummyProduct("قاب سیلیکونی آیفون ۱۵", 250000L),
            DummyProduct("کاور شفاف آیفون ۱۵", 180000L)
        )
        val filtered = AccessoryAndOutlierFilter.filter(items, "قاب\u200Cگوشی آیفون ۱۵")
        assertEquals(2, filtered.primaryResults.size)
        assertEquals(0, filtered.accessoryResults.size)
    }

    // --- Defect 2: Median Price Calculation Arithmetic Overflow ---

    @Test
    fun testDefect2_medianCalculationArithmeticOverflowSafe() {
        val extremePrices = listOf(
            Long.MAX_VALUE - 100L,
            Long.MAX_VALUE - 10L
        )
        val median = AccessoryAndOutlierFilter.calculateMedian(extremePrices)
        val expected = Long.MAX_VALUE - 55L
        assertEquals(expected, median)
        assertTrue("Median of positive extreme prices must remain positive without overflow", median > 0L)

        val fourExtreme = listOf(
            Long.MAX_VALUE - 400L,
            Long.MAX_VALUE - 300L,
            Long.MAX_VALUE - 100L,
            Long.MAX_VALUE - 50L
        )
        val medianFour = AccessoryAndOutlierFilter.calculateMedian(fourExtreme)
        val expectedFour = Long.MAX_VALUE - 200L
        assertEquals(expectedFour, medianFour)
    }

    // --- Defect 3: ZWNJ between Digits, Units, and Multipliers ---

    @Test
    fun testDefect3_unitExtractorZwnjSeparation() {
        // Gram with ZWNJ: ۲۰۰‌گرم
        val r1 = UnitExtractor.extract("۲۰۰\u200Cگرم", 10000L)
        assertNotNull(r1)
        assertEquals(200.0, r1!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, r1.standardUnit)
        assertEquals(1, r1.packCount)

        // Kg with ZWNJ: ۲‌کیلو
        val r2 = UnitExtractor.extract("۲\u200Cکیلو", 40000L)
        assertNotNull(r2)
        assertEquals(2000.0, r2!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, r2.standardUnit)
        assertEquals(1, r2.packCount)

        // Ml with ZWNJ: ۲۰۰‌میلی‌لیتر
        val r3 = UnitExtractor.extract("۲۰۰\u200Cمیلی\u200Cلیتر", 15000L)
        assertNotNull(r3)
        assertEquals(200.0, r3!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, r3.standardUnit)
        assertEquals(1, r3.packCount)

        // Pack count with ZWNJ: بسته‌۶‌عددی
        val r4 = UnitExtractor.extract("بسته\u200C۶\u200Cعددی", 30000L)
        assertNotNull(r4)
        assertEquals(6.0, r4!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.PIECE, r4.standardUnit)
        assertEquals(6, r4.packCount)

        // Compound inline with ZWNJ: ۶‌x‌۲۰۰‌میلی‌لیتر
        val r5 = UnitExtractor.extract("۶\u200Cx\u200C۲۰۰\u200Cمیلی\u200Cلیتر", 90000L)
        assertNotNull(r5)
        assertEquals(1200.0, r5!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, r5.standardUnit)
        assertEquals(6, r5.packCount)
    }

    // --- Defect 4: Genuine Fraction Slash and Persian Decimal Comma Parsing ---

    @Test
    fun testDefect4_fractionSlashAndDecimalComma() {
        // 1/2 kg = 500g
        val rHalfKg = UnitExtractor.extract("شیر ۱/۲ کیلوگرم", 50000L)
        assertNotNull(rHalfKg)
        assertEquals(500.0, rHalfKg!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, rHalfKg.standardUnit)
        assertEquals("0.5 کیلوگرم", rHalfKg.displayText)

        // 1/4 Liter = 250ml
        val rQuarterL = UnitExtractor.extract("روغن ۱/۴ لیتر", 25000L)
        assertNotNull(rQuarterL)
        assertEquals(250.0, rQuarterL!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, rQuarterL.standardUnit)

        // 3/4 kg = 750g
        val rThreeQuarter = UnitExtractor.extract("آرد ۳/۴ کیلوگرم", 30000L)
        assertNotNull(rThreeQuarter)
        assertEquals(750.0, rThreeQuarter!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.GRAM, rThreeQuarter.standardUnit)

        // Decimal comma (ASCII comma)
        val rComma = UnitExtractor.extract("شیر ۲,۵ لیتر", 60000L)
        assertNotNull(rComma)
        assertEquals(2500.0, rComma!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, rComma.standardUnit)

        // Decimal comma (Persian comma ،)
        val rPersianComma = UnitExtractor.extract("شیر ۲،۵ لیتر", 60000L)
        assertNotNull(rPersianComma)
        assertEquals(2500.0, rPersianComma!!.baseQuantity, 0.001)
        assertEquals(StandardUnit.MILLILITER, rPersianComma.standardUnit)
    }

    // --- Defect 5: Stripping Unicode Bidirectional Control Marks ---

    @Test
    fun testDefect5_unicodeBidiControlsStrippedFromStopWords() {
        // Invisible RLM (\u200F) and LRM (\u200E) surrounding stop word
        val wrappedName = "\u200Fرستوران\u200E شاندیز"
        val cleanName = "رستوران شاندیز"

        val normalizedWrapped = PersianNormalizer.normalize(wrappedName)
        val normalizedClean = PersianNormalizer.normalize(cleanName)
        assertEquals("رستوران شاندیز", normalizedWrapped)
        assertEquals(normalizedClean, normalizedWrapped)

        val tokens1 = FoodVendorMatcher.cleanVendorTokens(wrappedName)
        val tokens2 = FoodVendorMatcher.cleanVendorTokens(cleanName)
        assertEquals(setOf("شاندیز"), tokens1)
        assertEquals(setOf("شاندیز"), tokens2)

        val sim = FoodVendorMatcher.computeFuzzySimilarity(wrappedName, cleanName)
        assertEquals(1.0, sim, 0.001)
        assertTrue("Must match vendor above 0.80 threshold", sim >= 0.80)

        // Directional embedding markers \u202A through \u202E
        val embeddingName = "\u202Aکافه\u202C \u202Bویونا\u202C"
        val normalizedEmbedding = PersianNormalizer.normalize(embeddingName)
        assertEquals("کافه ویونا", normalizedEmbedding)
    }

    // --- Defect 6: HeaderSanitizer Token & Sensitive Auth Scrubbing ---

    @Test
    fun testDefect6_headerSanitizerAuthAndTokenScrubbing() {
        val sensitiveHeaders = mapOf(
            "X-Auth-Token" to "secret_snapp_token_9999",
            "x-auth-token" to "secret_snapp_token_8888",
            "token" to "secret_tapsi_token_7777",
            "Token" to "secret_digikala_token_6666",
            "x-token" to "secret_xtoken_5555",
            "auth-token" to "secret_auth_token_4444",
            "api-key" to "secret_api_key_3333",
            "X-Api-Key" to "secret_x_api_key_1",
            "x-api-key" to "secret_x_api_key_2",
            "X-API-KEY" to "secret_x_api_key_3",
            "apikey" to "secret_apikey_4",
            "api_key" to "secret_api_key_5",
            "app-secret" to "secret_app_value_2222",
            "Custom-Auth-Header" to "secret_custom_auth_1111",
            "session-cookie-id" to "session_xyz_0000",
            "Authorization" to "Bearer sensitive_bearer_token",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Host" to "api.snappfood.ir",
            "User-Agent" to "PriceHunter/1.0"
        )

        val sanitized = HeaderSanitizer.sanitizeHeaders(sensitiveHeaders)

        assertEquals("***MASKED***", sanitized["X-Auth-Token"])
        assertEquals("***MASKED***", sanitized["x-auth-token"])
        assertEquals("***MASKED***", sanitized["token"])
        assertEquals("***MASKED***", sanitized["Token"])
        assertEquals("***MASKED***", sanitized["x-token"])
        assertEquals("***MASKED***", sanitized["auth-token"])
        assertEquals("***MASKED***", sanitized["api-key"])
        assertEquals("***MASKED***", sanitized["X-Api-Key"])
        assertEquals("***MASKED***", sanitized["x-api-key"])
        assertEquals("***MASKED***", sanitized["X-API-KEY"])
        assertEquals("***MASKED***", sanitized["apikey"])
        assertEquals("***MASKED***", sanitized["api_key"])
        assertEquals("***MASKED***", sanitized["app-secret"])
        assertEquals("***MASKED***", sanitized["Custom-Auth-Header"])
        assertEquals("***MASKED***", sanitized["session-cookie-id"])
        assertEquals("***MASKED***", sanitized["Authorization"])

        // Non-sensitive headers must not be masked
        assertEquals("application/json", sanitized["Content-Type"])
        assertEquals("application/json", sanitized["Accept"])
        assertEquals("api.snappfood.ir", sanitized["Host"])
        assertEquals("PriceHunter/1.0", sanitized["User-Agent"])

        // Verify inside DebugNetworkCapturePool
        val pool = DebugNetworkCapturePool()
        val entry = NetworkCaptureEntry(
            method = "POST",
            url = "https://api.tapsi.food/v1/auth",
            statusCode = 200,
            durationMs = 120L,
            requestHeaders = mapOf(
                "X-Auth-Token" to "raw_token_value",
                "X-Api-Key" to "raw_api_key_value",
                "Accept" to "application/json",
                "Host" to "api.tapsi.food"
            )
        )
        pool.logCapture(entry)

        val captures = pool.getCaptures()
        assertEquals(1, captures.size)
        assertEquals("***MASKED***", captures[0].requestHeaders["X-Auth-Token"])
        assertEquals("***MASKED***", captures[0].requestHeaders["X-Api-Key"])
        assertEquals("application/json", captures[0].requestHeaders["Accept"])
        assertEquals("api.tapsi.food", captures[0].requestHeaders["Host"])
    }

    @Test
    fun testDefect6_apiKeyHeaderVariantsMaskedAndStandardHeadersPreserved() {
        val apiKeyVariants = mapOf(
            "x-api-key" to "key_val_1",
            "X-Api-Key" to "key_val_2",
            "X-API-KEY" to "key_val_3",
            "apikey" to "key_val_4",
            "ApiKey" to "key_val_5",
            "APIKEY" to "key_val_6",
            "api_key" to "key_val_7",
            "Api_Key" to "key_val_8",
            "API_KEY" to "key_val_9",
            "api-key" to "key_val_10",
            "API-KEY" to "key_val_11",
            "Sec-Key" to "key_val_12",
            "Client-Key" to "key_val_13"
        )
        val sanitizedKeys = HeaderSanitizer.sanitizeHeaders(apiKeyVariants)
        apiKeyVariants.keys.forEach { headerKey ->
            assertEquals("Header '$headerKey' must be masked", "***MASKED***", sanitizedKeys[headerKey])
        }

        val standardHeaders = mapOf(
            "Host" to "api.digikala.com",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
            "Accept" to "text/html,application/json",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "fa-IR,fa;q=0.9",
            "Content-Type" to "application/json; charset=utf-8",
            "Content-Length" to "1024",
            "Connection" to "keep-alive",
            "Origin" to "https://torob.com",
            "Referer" to "https://torob.com/search/",
            "Cache-Control" to "max-age=0, no-cache",
            "ETag" to "\"33a64df551425fcc55e4d42a148795d9f25f89d4\""
        )
        val sanitizedStandard = HeaderSanitizer.sanitizeHeaders(standardHeaders)
        standardHeaders.forEach { (key, expectedValue) ->
            assertEquals("Standard header '$key' must remain unmasked", expectedValue, sanitizedStandard[key])
        }
    }

    // --- Defect 7: TapsiFoodProvider Individual Dish Cost Formula Calculation ---

    @Test
    fun testDefect7_tapsiFoodDeliveredCostFormulaIntegrity() = runBlocking {
        val server = MockWebServer()
        val searchJson = """
        {
          "status": 200,
          "data": {
            "products": [
              {
                "id": "dish-cheap-1",
                "title": "سیب زمینی سرخ کرده",
                "basePrice": 50000,
                "discountPrice": 40000,
                "vendor": {
                  "vendorCode": "v-1",
                  "vendorTitle": "رستوران شاندیز",
                  "latitude": 35.75,
                  "longitude": 51.41
                }
              },
              {
                "id": "dish-expensive-2",
                "title": "چلو کباب برگ شاندیز",
                "basePrice": 450000,
                "discountPrice": 400000,
                "vendor": {
                  "vendorCode": "v-1",
                  "vendorTitle": "رستوران شاندیز",
                  "latitude": 35.75,
                  "longitude": 51.41
                }
              }
            ]
          }
        }
        """.trimIndent()

        val validateJson = """
        {
          "status": 200,
          "data": {
            "quote": {
              "packagingFee": 12000,
              "deliveryFee": 30000,
              "serviceFee": 5000,
              "tax": 29700,
              "totalDeliveredCost": 326700,
              "minOrderThreshold": 120000
            }
          }
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(searchJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(validateJson))
        server.start()

        try {
            val provider = TapsiFoodProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchFoodDishes("غذا", GeoLocation(35.75, 51.41))
            assertTrue("Expected success result", result is ProviderResult.Success)

            val dishes = (result as ProviderResult.Success).data
            assertEquals(2, dishes.size)

            val cheapDish = dishes[0]
            val expDish = dishes[1]

            // Verify each dish has its own authentic total delivered cost:
            // totalCost = (basePrice - discountToman) + packaging + delivery + service + tax
            // Cheap dish: (50000 - 10000) + 12000 + 30000 + 5000 + 29700 = 116700
            val expectedCheapTotal = (50000L - 10000L) + 12000L + 30000L + 5000L + 29700L
            assertEquals(expectedCheapTotal, cheapDish.costBreakdown.totalCostToman)

            // Expensive dish: (450000 - 50000) + 12000 + 30000 + 5000 + 29700 = 476700
            val expectedExpTotal = (450000L - 50000L) + 12000L + 30000L + 5000L + 29700L
            assertEquals(expectedExpTotal, expDish.costBreakdown.totalCostToman)

            // Critically: Dishes with different base prices MUST NOT share the same hardcoded total!
            assertNotEquals(cheapDish.costBreakdown.totalCostToman, expDish.costBreakdown.totalCostToman)

            for (dish in dishes) {
                val b = dish.costBreakdown
                val expectedTotal = (b.foodPriceToman - b.discountToman) +
                        b.packagingFeeToman + b.deliveryFeeToman + b.serviceFeeToman + b.taxToman
                assertEquals(expectedTotal, b.totalCostToman)
            }
        } finally {
            server.shutdown()
        }
    }
}
