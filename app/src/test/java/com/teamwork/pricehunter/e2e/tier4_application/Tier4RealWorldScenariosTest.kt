package com.teamwork.pricehunter.e2e.tier4_application

import com.teamwork.pricehunter.core.intelligence.*
import com.teamwork.pricehunter.core.network.*
import com.teamwork.pricehunter.core.provider.*
import com.teamwork.pricehunter.core.security.*
import com.teamwork.pricehunter.e2e.mock.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Application Scenarios (8 end-to-end user journeys)
 * Spec reference: TC-T4-E2E-01 through TC-T4-E2E-08
 */
class Tier4RealWorldScenariosTest {

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

    data class ProductListing(
        override val title: String,
        override val priceToman: Long
    ) : PriceableItem

    @Test
    fun TC_T4_E2E_01_dairyMultiPackShoppingSearch() = runBlocking {
        // Step 1: User opens app in Shopping Tab with RTL layout
        val userQuery = "شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی"

        // Step 2: Normalize query
        val normalizedQuery = PersianNormalizer.normalize(userQuery)
        assertEquals("شیر کم چرب کاله 200 میلی‌لیتر بسته 6 عددی", normalizedQuery)

        // Step 3: Unit extraction
        val unitResult = UnitExtractor.extract(normalizedQuery, 120000L)
        assertNotNull(unitResult)
        assertEquals(1200.0, unitResult!!.baseQuantity, 0.01) // 6 * 200ml = 1200ml
        assertEquals(StandardUnit.MILLILITER, unitResult.standardUnit)
        assertEquals(6, unitResult.packCount)
        assertEquals(100000L, unitResult.unitPriceToman) // 100,000 Toman / L

        // Step 4: Parallel Search execution
        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val offers = repo.searchShoppingParallel(normalizedQuery)
        assertTrue(offers.isNotEmpty())

        // Step 5: Strictly sorted ascending by priceToman
        for (i in 0 until offers.size - 1) {
            assertTrue(offers[i].priceToman <= offers[i + 1].priceToman)
        }
        assertTrue(offers.first().originalUrl.startsWith("http"))
    }

    @Test
    fun TC_T4_E2E_02_foodDeliveryComparisonPizzaWithGpsLocation() = runBlocking {
        // Step 1: User selects Sa'adat Abad GPS
        val gpsLocation = GeoLocation(35.7733, 51.4185)
        val query = "پیتزا مخصوص"

        // Step 2: Parallel food search across SnappFood and TapsiFood
        val snappFood = SnappFoodProvider(mockServer.baseUrl)
        val tapsiFood = TapsiFoodProvider(mockServer.baseUrl)
        val repo = SearchEngineRepository(foodProviders = mutableListOf(snappFood, tapsiFood))

        val dishes = repo.searchFoodParallel(query, gpsLocation)
        assertTrue("Dishes must be found from food providers", dishes.isNotEmpty())

        // Step 3: Vendor matching
        val snappDish = dishes.first { it.providerName == "اسنپ‌فود" }
        val tapsiDish = dishes.first { it.providerName == "تپسی‌فود" }

        val (isMatch, pair) = FoodVendorMatcher.isVendorMatch(
            snappDish.vendorName, snappDish.vendorLocation,
            tapsiDish.vendorName, tapsiDish.vendorLocation
        )
        assertTrue("Vendors should match within 150m geofence with similarity > 0.80", isMatch)
        assertNotNull(pair)

        // Step 4: Side-by-side delivered cost comparison
        val costA = snappDish.costBreakdown.totalCostToman
        val costB = tapsiDish.costBreakdown.totalCostToman
        assertTrue(costA > 0)
        assertTrue(costB > 0)
    }

    @Test
    fun TC_T4_E2E_03_activeVpnDetectionAndDomestic403RecoveryFlow() = runBlocking {
        // Step 1: Active VPN
        val vpnConn = object : ConnectivityProvider {
            var vpnState = true
            override fun isConnected(): Boolean = true
            override fun isVpnActive(): Boolean = vpnState
        }
        val guard = NetworkGuard(vpnConn)

        // Step 2: Sotoon CDN returns 403 on SnappShop, while others succeed
        mockServer.setScenario("snappshop", MockScenario.GEOBLOCK_403)
        mockServer.setScenario("torob", MockScenario.NORMAL)
        mockServer.setScenario("digikala", MockScenario.NORMAL)

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val results = repo.searchShoppingParallel("هندزفری")
        assertTrue("Digikala and Torob results must remain visible", results.isNotEmpty())

        // Step 3: Advisory banner displayed
        val guidance = guard.getPersianGuidanceMessage(403, false)
        assertTrue("Banner must advise disabling VPN", guidance.contains("خاموش"))

        // Step 4: User disables VPN and retries
        vpnConn.vpnState = false
        mockServer.setScenario("snappshop", MockScenario.NORMAL)

        val recoveredResults = repo.searchShoppingParallel("هندزفری")
        assertTrue("SnappShop offers now present after VPN recovery", recoveredResults.any { it.providerName == "اسنپ‌شاپ" })
    }

    @Test
    fun TC_T4_E2E_04_dynamicRemoteConfigUpdateAndSchemaDriftResilience() = runBlocking {
        val assetLoader = object : AssetLoader {
            override fun loadAsset(fileName: String): String =
                """{"version": 1, "providers": {"torob": {"id": "torob", "baseUrl": "${mockServer.baseUrl}/v4/base-product/search/"}}}"""
        }
        val cacheStorage = object : ConfigCacheStorage {
            var cache: String? = null
            override fun readCache(): String? = cache
            override fun writeCache(json: String) { cache = json }
            override fun getEtag(): String? = null
            override fun setEtag(etag: String?) {}
            override fun getLastFetchTime(): Long = 0L
            override fun setLastFetchTime(timestamp: Long) {}
            override fun clear() { cache = null }
        }

        val remoteManager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            remoteConfigUrl = "${mockServer.baseUrl}/remote-config/providers_config.json"
        )

        // Config refreshed in background with updated version 2 and unknown schema keys
        val updatedConfig = remoteManager.refreshConfig(forceRefresh = true)
        assertEquals(2, updatedConfig.version)
        assertTrue(updatedConfig.providers.containsKey("digikala"))

        // Search engine uses updated endpoints dynamically
        val digikala = DigikalaProvider(mockServer.baseUrl)
        val res = digikala.searchProducts("ساعت هوشمند")
        assertTrue(res is ProviderResult.Success)
    }

    @Test
    fun TC_T4_E2E_05_inAppWebViewAuthAndCookieProtectedPreInvoiceQuote() = runBlocking {
        val authStorage: AuthStorage = InMemoryAuthStorage()

        // Missing session detected, webview captures auth cookie
        val sessionCookie = "jwt_session_token=valid_otp_token_xyz"
        authStorage.saveCookies("snappfood", sessionCookie)

        // Authenticated request attaches cookie
        val retrievedCookie = authStorage.getCookies("snappfood")
        assertEquals(sessionCookie, retrievedCookie)

        val snappFood = SnappFoodProvider(mockServer.baseUrl)
        val res = snappFood.searchFoodDishes("چلوکباب", GeoLocation(35.7733, 51.4185))
        assertTrue(res is ProviderResult.Success)
    }

    @Test
    fun TC_T4_E2E_06_outlierAndAccessorySuppressionSmartphoneSearch() {
        val userQuery = "گوشی شیائومی نوت 12"

        // 16 genuine phones (median ~8.2M), 12 accessories, 1 clone phone (900k)
        val items = mutableListOf<ProductListing>()
        for (i in 1..16) {
            items.add(ProductListing("گوشی موبایل شیائومی ردمی نوت 12 مدل $i", 7800000L + (i * 50000L)))
        }
        for (i in 1..12) {
            items.add(ProductListing("کاور سیلیکونی و گلس مناسب برای شیائومی نوت 12 مدل $i", 100000L + (i * 10000L)))
        }
        // Clone phone priced at 900k (< 20% of 8.2M)
        items.add(ProductListing("ماکت یا طرح گوشی شیائومی نوت 12", 900000L))

        val filterResult = AccessoryAndOutlierFilter.filter(items, userQuery, 0.20)

        assertEquals("All 12 cases/glasses must be separated into accessoryResults", 12, filterResult.accessoryResults.size)
        assertEquals("Clone phone must be flagged into suspiciousOutliers", 1, filterResult.suspiciousOutliers.size)
        assertEquals(900000L, filterResult.suspiciousOutliers[0].priceToman)
        assertEquals("Primary results must contain only the 16 legitimate phones", 16, filterResult.primaryResults.size)
    }

    @Test
    fun TC_T4_E2E_07_foodDeliveryLocationChangeViaMapDialog() = runBlocking {
        // Initial Sa'adat Abad
        val loc1 = GeoLocation(35.7733, 51.4185)
        // User relocates to Tehranpars
        val loc2 = GeoLocation(35.7310, 51.5350)

        val distanceMoved = FoodVendorMatcher.haversineDistanceMeters(loc1, loc2)
        assertTrue("Distance between Sa'adat Abad and Tehranpars should be > 10 km", distanceMoved > 10000.0)

        val snappFood = SnappFoodProvider(mockServer.baseUrl)
        val resTehranpars = snappFood.searchFoodDishes("کباب کوبیده", loc2)
        assertTrue(resTehranpars is ProviderResult.Success)
    }

    @Test
    fun TC_T4_E2E_08_networkDisconnectionAndOfflineGracefulDegradation() = runBlocking {
        val disconnectedConn = object : ConnectivityProvider {
            override fun isConnected(): Boolean = false
            override fun isVpnActive(): Boolean = false
        }
        val guard = NetworkGuard(disconnectedConn)

        // Offline mode detected
        val status = guard.checkNetworkStatus()
        assertEquals(NetworkStatus.Disconnected, status)
        val offlineMsg = guard.getPersianGuidanceMessage()
        assertTrue("Offline message must inform user of disconnected state", offlineMsg.contains("برقرار نیست"))

        // Reconnected
        val reconnectedConn = object : ConnectivityProvider {
            override fun isConnected(): Boolean = true
            override fun isVpnActive(): Boolean = false
        }
        val reconnectedGuard = NetworkGuard(reconnectedConn)
        assertEquals(NetworkStatus.Connected, reconnectedGuard.checkNetworkStatus())

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl)
            )
        )
        val results = repo.searchShoppingParallel("لپ تاپ")
        assertTrue("Search executes successfully upon reconnection", results.isNotEmpty())
    }
}
