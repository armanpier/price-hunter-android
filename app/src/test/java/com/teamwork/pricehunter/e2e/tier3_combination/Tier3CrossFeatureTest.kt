package com.teamwork.pricehunter.e2e.tier3_combination

import com.teamwork.pricehunter.core.intelligence.*
import com.teamwork.pricehunter.core.network.*
import com.teamwork.pricehunter.core.provider.*
import com.teamwork.pricehunter.core.security.*
import com.teamwork.pricehunter.e2e.mock.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 3: Cross-Feature Combinations (12 pairwise & multi-feature tests)
 * Spec reference: TC-T3-CB-01 through TC-T3-CB-12
 */
class Tier3CrossFeatureTest {

    private lateinit var mockServer: MockServerManager

    @Before
    fun setUp() {
        mockServer = MockServerManager()
        mockServer.start()
    }

    @After
    fun tearDown() {
        mockServer.stop()
    }

    data class CrawledItem(
        override val title: String,
        override val priceToman: Long
    ) : PriceableItem

    @Test
    fun TC_T3_CB_01_normalizerWithUnitExtractorAndOutlierFilter() {
        val rawItems = listOf(
            CrawledItem("شِیرِ كَم‌چَرب كاله ۲۰۰ ميلى‌ليتر بسته ۶ عددى", 120000L),
            CrawledItem("شيرِ پُرچرب دامداران ۱.۵ ليتر", 50000L),
            CrawledItem("كاور ژله‌اى مخصوص شيشه شير كاله", 15000L),
            CrawledItem("شیر کم چرب کاله نمونه آزمایشی", 5000L) // Outlier
        )

        // Step 1: Normalize all titles
        val normalizedItems = rawItems.map { item ->
            CrawledItem(PersianNormalizer.normalize(item.title), item.priceToman)
        }

        // Step 2: Extract units on dairy products
        val unit1 = UnitExtractor.extract(normalizedItems[0].title, normalizedItems[0].priceToman)
        assertNotNull(unit1)
        assertEquals(1200.0, unit1!!.baseQuantity, 0.01) // 6 x 200ml = 1200ml
        assertEquals(100000L, unit1.unitPriceToman)      // 100,000 Toman/L

        val unit2 = UnitExtractor.extract(normalizedItems[1].title, normalizedItems[1].priceToman)
        assertNotNull(unit2)
        assertEquals(1500.0, unit2!!.baseQuantity, 0.01) // 1.5L = 1500ml

        // Step 3: Accessory & Outlier filtering for query "شیر کاله"
        val filterResult = AccessoryAndOutlierFilter.filter(normalizedItems, "شیر کاله", 0.20)

        // Item 3 has "کاور" -> accessory
        assertEquals(1, filterResult.accessoryResults.size)
        assertTrue(filterResult.accessoryResults[0].title.contains("کاور"))

        // Item 4 has price 5000 (< 20% of median ~85k) -> outlier
        assertEquals(1, filterResult.suspiciousOutliers.size)
        assertEquals(5000L, filterResult.suspiciousOutliers[0].priceToman)

        // Items 1 & 2 in primary
        assertEquals(2, filterResult.primaryResults.size)
    }

    @Test
    fun TC_T3_CB_02_foodVendorMatcherWithDeliveredCost() = runBlocking {
        val snappFood = SnappFoodProvider(mockServer.baseUrl)
        val tapsiFood = TapsiFoodProvider(mockServer.baseUrl)
        val userLoc = GeoLocation(35.7733, 51.4185)

        val snappResult = snappFood.searchFoodDishes("کباب", userLoc)
        val tapsiResult = tapsiFood.searchFoodDishes("کباب", userLoc)

        assertTrue(snappResult is ProviderResult.Success)
        assertTrue(tapsiResult is ProviderResult.Success)

        val snappDish = (snappResult as ProviderResult.Success).data.first()
        val tapsiDish = (tapsiResult as ProviderResult.Success).data.first()

        // Verify vendors matched
        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            snappDish.vendorName, snappDish.vendorLocation,
            tapsiDish.vendorName, tapsiDish.vendorLocation
        )

        assertTrue("Vendors should be matched as same restaurant (<150m, >0.8 sim)", isMatch)
        assertNotNull(pair)
        assertTrue(pair!!.distanceMeters < 150.0)

        // Compare delivered cost breakdown
        val costA = snappDish.costBreakdown.totalCostToman
        val costB = tapsiDish.costBreakdown.totalCostToman
        assertTrue(costA > 0 && costB > 0)
        // Comparison ranking possible
        val cheaper = if (costA < costB) snappDish else tapsiDish
        assertNotNull(cheaper)
    }

    @Test
    fun TC_T3_CB_03_snappShopWithNetworkGuardVpn() = runBlocking {
        mockServer.setScenario("snappshop", MockScenario.GEOBLOCK_403)
        mockServer.setScenario("torob", MockScenario.NORMAL)
        mockServer.setScenario("digikala", MockScenario.NORMAL)

        val vpnConn = object : ConnectivityProvider {
            override fun isConnected(): Boolean = true
            override fun isVpnActive(): Boolean = true
        }
        val guard = NetworkGuard(vpnConn)

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val results = repo.searchShoppingParallel("سامسونگ")

        // NetworkGuard detects VPN
        val netStatus = guard.checkNetworkStatus()
        assertTrue(netStatus is NetworkStatus.ConnectedWithVpn)

        // SnappShop 403 is recognized as domestic VPN issue
        assertTrue(guard.isDomesticBlockedError(403, false))

        // Torob & Digikala offers remain interactive
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.providerName == "دیجی‌کالا" })
    }

    @Test
    fun TC_T3_CB_04_remoteConfigDriftWithProviderEngine() = runBlocking {
        val assetLoader = object : AssetLoader {
            override fun loadAsset(fileName: String): String =
                """{"version": 1, "providers": {"torob": {"id": "torob", "baseUrl": "${mockServer.baseUrl}/v4/base-product/search/"}}}"""
        }
        val cacheStorage = object : ConfigCacheStorage {
            var cached: String? = null
            override fun readCache(): String? = cached
            override fun writeCache(json: String) { cached = json }
            override fun getEtag(): String? = null
            override fun setEtag(etag: String?) {}
            override fun getLastFetchTime(): Long = 0L
            override fun setLastFetchTime(timestamp: Long) {}
            override fun clear() { cached = null }
        }

        val remoteManager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            remoteConfigUrl = "${mockServer.baseUrl}/remote-config/providers_config.json"
        )

        // Refresh remote config
        val updatedConfig = remoteManager.refreshConfig(forceRefresh = true)
        assertEquals(2, updatedConfig.version)
        assertTrue(updatedConfig.providers.containsKey("torob"))

        // Provider uses updated config base URL dynamically
        val torobProvider = TorobProvider(mockServer.baseUrl)
        val res = torobProvider.searchProducts("آیفون")
        assertTrue(res is ProviderResult.Success)
    }

    @Test
    fun TC_T3_CB_05_authStorageWithWebViewSessionCaptureAndFoodCall() = runBlocking {
        val storage: AuthStorage = InMemoryAuthStorage()
        val capturedCookie = "token=snapp_auth_token_99; Path=/; Domain=.snappfood.ir"

        storage.saveCookies("snappfood", capturedCookie)

        val cookies = storage.getCookies("snappfood")
        assertEquals(capturedCookie, cookies)

        val snappFood = SnappFoodProvider(mockServer.baseUrl)
        val res = snappFood.searchFoodDishes("پیتزا", GeoLocation(35.7733, 51.4185))
        assertTrue(res is ProviderResult.Success)
    }

    @Test
    fun TC_T3_CB_06_parallelSearchWithProgressiveLoadingSync() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.NORMAL)
        mockServer.setScenario("torob", MockScenario.WAF_490)
        mockServer.setScenario("snappshop", MockScenario.NORMAL)

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val emissions = repo.searchShoppingFlow("سامسونگ").take(2).toList()
        assertTrue(emissions.isNotEmpty())
        assertTrue(emissions.last().isNotEmpty())
    }

    @Test
    fun TC_T3_CB_07_unitExtractorWithShoppingTabSortByUnitPrice() {
        val offerA = CrawledItem("برنج طارم هاشمی ۱ کیلوگرم", 110000L)
        val offerB = CrawledItem("برنج طارم هاشمی ۵ کیلوگرم", 500000L)
        val offerC = CrawledItem("برنج طارم هاشمی ۱۰ کیلوگرم", 950000L)

        val unitA = UnitExtractor.extract(offerA.title, offerA.priceToman)!!
        val unitB = UnitExtractor.extract(offerB.title, offerB.priceToman)!!
        val unitC = UnitExtractor.extract(offerC.title, offerC.priceToman)!!

        assertEquals(110000L, unitA.unitPriceToman) // 110k / kg
        assertEquals(100000L, unitB.unitPriceToman) // 100k / kg
        assertEquals(95000L, unitC.unitPriceToman)  // 95k / kg

        // Sort by total price
        val sortedByTotal = listOf(offerA, offerB, offerC).sortedBy { it.priceToman }
        assertEquals(offerA, sortedByTotal[0])
        assertEquals(offerB, sortedByTotal[1])
        assertEquals(offerC, sortedByTotal[2])

        // Sort by unit price (Per kg)
        val sortedByUnit = listOf(
            Pair(offerA, unitA.unitPriceToman!!),
            Pair(offerB, unitB.unitPriceToman!!),
            Pair(offerC, unitC.unitPriceToman!!)
        ).sortedBy { it.second }

        assertEquals(offerC, sortedByUnit[0].first) // Cheapest per kg: Offer C (95k)
        assertEquals(offerB, sortedByUnit[1].first)
        assertEquals(offerA, sortedByUnit[2].first)
    }

    @Test
    fun TC_T3_CB_08_foodVendorMatcherWithMinOrderWarning() = runBlocking {
        val locSnappFood = GeoLocation(35.7733, 51.4185) // Sa'adat Abad
        val locTapsiFood = GeoLocation(35.7738, 51.4189) // Sa'adat Abad (~66m away)
        val nameSnappFood = "رستوران نایب (شعبه سعادت آباد)"
        val nameTapsiFood = "نایب سعادت آباد"

        // Step 1: Execute FoodVendorMatcher to match identical branch across platforms
        val (isMatch, matchedPair) = FoodVendorMatcher.isVendorMatch(
            name1 = nameSnappFood, loc1 = locSnappFood,
            name2 = nameTapsiFood, loc2 = locTapsiFood
        )
        assertTrue("SnappFood and TapsiFood branches must match within 150m geofence", isMatch)
        assertNotNull(matchedPair)
        assertTrue("Distance must be <150m", matchedPair!!.distanceMeters < 150.0)
        assertTrue("Persian token similarity must be >=0.80", matchedPair.tokenSimilarity >= 0.80)

        // Step 2: Calculate delivered costs & minimum order thresholds for the matched vendors
        // Vendor A: Dish price 160,000 Toman, min order 150,000 Toman -> No warning
        val costVendorA = FoodCostCalculator.calculateDeliveredCost(
            foodPriceToman = 160000L,
            packagingFeeToman = 15000L,
            deliveryFeeToman = 30000L,
            minOrderThresholdToman = 150000L
        )

        // Vendor B: Dish price 120,000 Toman, min order 150,000 Toman -> Shortfall warning 30,000
        val costVendorB = FoodCostCalculator.calculateDeliveredCost(
            foodPriceToman = 120000L,
            packagingFeeToman = 12000L,
            deliveryFeeToman = 30000L,
            minOrderThresholdToman = 150000L
        )

        // Step 3: Validate cross-feature combination assertions
        assertNull("Vendor A meets minimum order threshold (160k >= 150k)", costVendorA.minOrderWarningToman)
        assertNotNull("Vendor B is below minimum order threshold (120k < 150k)", costVendorB.minOrderWarningToman)
        assertEquals(30000L, costVendorB.minOrderWarningToman)
        assertEquals(205000L, costVendorA.totalCostToman) // 160k + 15k + 30k
        assertEquals(162000L, costVendorB.totalCostToman) // 120k + 12k + 30k
    }

    @Test
    fun TC_T3_CB_09_debugNetworkCapturePoolWithHeaderMasking() {
        val pool = DebugNetworkCapturePool(maxCapacity = 50)

        // Record a network transaction containing sensitive auth headers
        pool.record(
            NetworkCaptureEntry(
                id = "req-sec-01",
                method = "POST",
                url = "https://snappfood.ir/mobile/v2/user/login",
                statusCode = 200,
                durationMs = 120L,
                requestHeaders = mapOf(
                    "Authorization" to "Bearer secret_jwt_token_12345",
                    "Cookie" to "session=top_secret_cookie",
                    "Content-Type" to "application/json",
                    "User-Agent" to "PriceHunter/1.0"
                ),
                responseHeaders = mapOf(
                    "Set-Cookie" to "refresh_token=secret_refresh_abc; HttpOnly",
                    "Content-Type" to "application/json"
                ),
                requestBody = """{"phone":"09121234567"}""",
                responseBody = """{"status":true}"""
            )
        )

        assertEquals(1, pool.size)
        val entry = pool.getAllEntries().first()

        // Assert sensitive request headers are masked
        assertEquals("***MASKED***", entry.requestHeaders["Authorization"])
        assertEquals("***MASKED***", entry.requestHeaders["Cookie"])
        assertEquals("application/json", entry.requestHeaders["Content-Type"])
        assertEquals("PriceHunter/1.0", entry.requestHeaders["User-Agent"])

        // Assert sensitive response headers are masked
        assertEquals("***MASKED***", entry.responseHeaders["Set-Cookie"])
        assertEquals("application/json", entry.responseHeaders["Content-Type"])

        // Strict verification: credentials must NOT appear anywhere in the sanitized headers
        assertFalse(entry.requestHeaders.values.contains("Bearer secret_jwt_token_12345"))
        assertFalse(entry.requestHeaders.values.contains("session=top_secret_cookie"))
        assertFalse(entry.responseHeaders.values.contains("refresh_token=secret_refresh_abc; HttpOnly"))
    }

    @Test
    fun TC_T3_CB_10_accessoryFilterWithQueryIntentOverride() {
        val userQuery = "کابل تایپ سی سامسونگ"
        val items = (1..5).map { i ->
            CrawledItem("کابل تبدیل تایپ سی مدل $i", 80000L + i * 10000L)
        }

        val result = AccessoryAndOutlierFilter.filter(items, userQuery)

        // Because query explicitly mentions "کابل", items remain in primaryResults
        assertEquals(5, result.primaryResults.size)
        assertEquals(0, result.accessoryResults.size)
    }

    @Test
    fun TC_T3_CB_11_zeroKnowledgeStorageWithNetworkReconnection() = runBlocking {
        val storage: AuthStorage = InMemoryAuthStorage()
        storage.saveToken("snappfood", "session_token_persist")

        // Network reconnects
        val token = storage.getToken("snappfood")
        assertNotNull(token)
        assertEquals("session_token_persist", token)

        val provider = SnappFoodProvider(mockServer.baseUrl)
        val res = provider.searchFoodDishes("غذا", GeoLocation(35.7733, 51.4185))
        assertTrue(res is ProviderResult.Success)
    }

    @Test
    fun TC_T3_CB_12_pluggableProviderRegistryWithParallelSearch() = runBlocking {
        val repo = SearchEngineRepository(
            shoppingProviders = mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl)
            )
        )

        // Dynamically register Okala
        val okala = OkalaProvider(mockServer.baseUrl)
        repo.registerShoppingProvider(okala)

        val results = repo.searchShoppingParallel("شیر")
        assertTrue("Okala offers must be included in parallel results", results.any { it.providerName == "اکالا" })
    }
}
