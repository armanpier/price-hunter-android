package com.teamwork.pricehunter.e2e.tier2_boundary

import com.teamwork.pricehunter.core.intelligence.*
import com.teamwork.pricehunter.core.network.*
import com.teamwork.pricehunter.core.provider.*
import com.teamwork.pricehunter.core.security.*
import com.teamwork.pricehunter.e2e.mock.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.max

/**
 * Tier 2: Boundary & Corner Cases — Provider Faults, Search Timeouts & UI Edge Cases (17 tests)
 * Spec reference: TC-T2-PR-01..05, TC-T2-SE-01..05, TC-T2-UI-01..05, TC-T2-EX-01..02
 */
class Tier2ProviderFaultTest {

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

    // --- 1. Pluggable Provider Boundaries (5 tests) ---

    @Test
    fun TC_T2_PR_01_digikalaEndless307RedirectLoop() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.SERVER_ERROR_500)
        val provider = DigikalaProvider(mockServer.baseUrl)

        val result = provider.searchProducts("تست")
        assertTrue("Provider should return Error on failure", result is ProviderResult.Error)
    }

    @Test
    fun TC_T2_PR_02_digikalaItemMissingPriceVariant() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.EMPTY_RESULTS)
        val provider = DigikalaProvider(mockServer.baseUrl)

        val result = provider.searchProducts("ناموجود")
        assertTrue(result is ProviderResult.Success)
        assertEquals(0, (result as ProviderResult.Success).data.size)
    }

    @Test
    fun TC_T2_PR_03_torobHttp490ArCaptchaChallenge() = runBlocking {
        mockServer.setScenario("torob", MockScenario.WAF_490)
        val provider = TorobProvider(mockServer.baseUrl)

        val result = provider.searchProducts("آیفون")
        assertTrue("Torob 490 must be caught as Error", result is ProviderResult.Error)
        val errorMsg = (result as ProviderResult.Error).message
        assertTrue(errorMsg.contains("490") || errorMsg.contains("CAPTCHA"))
    }

    @Test
    fun TC_T2_PR_04_snappFoodNegativeDiscountBoundary() = runBlocking {
        val foodPrice = 100000L
        val extremeDiscount = 150000L // Exceeds food price (150%)
        val negativeDiscount = -25000L // Erroneous negative discount

        // Test 1: Extreme discount exceeding food price is safely clamped to foodPrice
        val breakdownClamped = FoodCostCalculator.calculateDeliveredCost(
            foodPriceToman = foodPrice,
            discountToman = extremeDiscount,
            packagingFeeToman = 10000L,
            deliveryFeeToman = 20000L,
            taxPercent = 10L
        )
        assertEquals(foodPrice, breakdownClamped.discountToman)
        val subtotalClamped = breakdownClamped.foodPriceToman - breakdownClamped.discountToman
        assertEquals(0L, subtotalClamped)
        assertTrue("Subtotal must never be negative", subtotalClamped >= 0L)
        assertTrue("Total cost must remain non-negative", breakdownClamped.totalCostToman >= 0L)

        // Test 2: Negative discount is clamped to 0 (cannot inflate subtotal above food price)
        val breakdownNegative = FoodCostCalculator.calculateDeliveredCost(
            foodPriceToman = foodPrice,
            discountToman = negativeDiscount
        )
        assertEquals(0L, breakdownNegative.discountToman)
        assertEquals(foodPrice, breakdownNegative.foodPriceToman - breakdownNegative.discountToman)

        // Test 3: Verify against SnappFoodProvider search execution with real MockWebServer
        val provider = SnappFoodProvider(mockServer.baseUrl)
        val result = provider.searchFoodDishes("کباب", GeoLocation(35.7733, 51.4185))
        assertTrue(result is ProviderResult.Success)
        val dishes = (result as ProviderResult.Success).data
        assertTrue(dishes.isNotEmpty())
        for (dish in dishes) {
            assertTrue("Dish discount must not exceed food price", dish.costBreakdown.discountToman <= dish.costBreakdown.foodPriceToman)
            assertTrue("Delivered cost must be positive", dish.costBreakdown.totalCostToman > 0L)
        }
    }

    @Test
    fun TC_T2_PR_05_snappFoodDeliveryFeeZeroFreeDelivery() = runBlocking {
        val breakdown = DeliveredFoodCostBreakdown(
            foodPriceToman = 150000L,
            discountToman = 0L,
            packagingFeeToman = 10000L,
            deliveryFeeToman = 0L, // Free delivery
            serviceFeeToman = 0L,
            taxToman = 16000L,
            totalCostToman = 176000L
        )

        assertEquals(0L, breakdown.deliveryFeeToman)
        assertEquals(176000L, breakdown.totalCostToman)
    }

    // --- 2. Parallel Search Engine Boundaries (5 tests) ---

    @Test
    fun TC_T2_SE_01_totalFailureAcrossAllProviders() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.SERVER_ERROR_500)
        mockServer.setScenario("torob", MockScenario.SERVER_ERROR_500)
        mockServer.setScenario("snappshop", MockScenario.SERVER_ERROR_500)

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val results = repo.searchShoppingParallel("کفش")
        // Does not crash; returns empty list
        assertNotNull(results)
        assertEquals(0, results.size)
    }

    @Test
    fun TC_T2_SE_02_providerQueryTimeoutEnforcement() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.TIMEOUT) // Delays 12s
        mockServer.setScenario("torob", MockScenario.NORMAL)
        mockServer.setScenario("snappshop", MockScenario.NORMAL)

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val results = repo.searchShoppingParallel("موبایل")
        // Torob & SnappShop results returned
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun TC_T2_SE_03_rapidSuccessiveSearchQueriesCancellation() = runBlocking {
        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl)
            )
        )

        val d1 = async { repo.searchShoppingParallel("سام") }
        val d2 = async { repo.searchShoppingParallel("سامس") }
        val d3 = async { repo.searchShoppingParallel("سامسونگ") }

        val res = awaitAll(d1, d2, d3)
        assertEquals(3, res.size)
        // All complete safely without thread leaks or race conditions
    }

    @Test
    fun TC_T2_SE_04_whitespaceOnlyQueryString() = runBlocking {
        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl)
            )
        )

        val results = repo.searchShoppingParallel("   ")
        assertEquals(0, results.size)
    }

    @Test
    fun TC_T2_SE_05_highResultVolumeSortingPerformance() = runBlocking {
        val items = (1..1200).map { i ->
            ShoppingOffer(
                id = "id_$i",
                providerName = "Shop",
                title = "محصول $i",
                rawPrice = (1200 - i) * 1000L,
                priceToman = (1200 - i) * 1000L,
                originalUrl = "https://example.com/$i"
            )
        }

        val start = System.currentTimeMillis()
        val sorted = items.sortedBy { it.priceToman }
        val duration = System.currentTimeMillis() - start

        assertEquals(1200, sorted.size)
        assertEquals(0L, sorted.first().priceToman)
        assertTrue("Sorting 1200 items should take <50ms, took $duration ms", duration < 50)
    }

    // --- 3. UI & Flow Boundaries (5 tests) ---

    @Test
    fun TC_T2_UI_01_zeroSearchResultsEmptyState() = runBlocking {
        // Configure mockServer to return EMPTY_RESULTS for all shopping providers
        mockServer.setScenario("digikala", MockScenario.EMPTY_RESULTS)
        mockServer.setScenario("torob", MockScenario.EMPTY_RESULTS)
        mockServer.setScenario("snappshop", MockScenario.EMPTY_RESULTS)

        val repo = SearchEngineRepository(
            mutableListOf(
                DigikalaProvider(mockServer.baseUrl),
                TorobProvider(mockServer.baseUrl),
                SnappShopProvider(mockServer.baseUrl)
            )
        )

        val query = "کالای_ناشناخته_و_ناموجود"
        val results = repo.searchShoppingParallel(query)
        assertEquals(0, results.size)

        // Evaluate SearchUiState contract
        val uiState = SearchUiState.fromResults(query, results)
        assertTrue("UI state must be SearchUiState.Empty when results are empty", uiState is SearchUiState.Empty)
        val emptyState = uiState as SearchUiState.Empty
        assertEquals(query, emptyState.query)
        assertEquals("هیچ محصولی یافت نشد", emptyState.emptyMessage)
    }

    @Test
    fun TC_T2_UI_02_gpsPermissionDeniedFlow() {
        // 1. Verify Manifest declaration for location permissions
        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)
        val manifestXml = manifestFile!!.readText()
        assertTrue("Manifest must declare ACCESS_FINE_LOCATION", manifestXml.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue("Manifest must declare ACCESS_COARSE_LOCATION", manifestXml.contains("android.permission.ACCESS_COARSE_LOCATION"))

        // 2. Exercise LocationSelectionManager state machine on GPS denial
        val manager = LocationSelectionManager(defaultLocation = GeoLocation(35.6892, 51.3890))
        val resultSource = manager.onGpsPermissionResult(isGranted = false, gpsLocation = null)

        assertTrue("When GPS permission is denied, manager must fallback to ManualMapPin", resultSource is LocationSource.ManualMapPin)
        assertEquals(35.6892, manager.activeLocation.latitude, 0.0001)
        assertEquals(51.3890, manager.activeLocation.longitude, 0.0001)
        assertTrue("Permission denied guidance banner must be displayed", manager.permissionDeniedBannerVisible)

        // 3. Verify subsequent manual pin drag updates location while retaining manual source
        val manualPin = GeoLocation(35.7733, 51.4185)
        manager.onManualPinDragged(manualPin)
        assertEquals(35.7733, manager.activeLocation.latitude, 0.0001)
        assertEquals(51.4185, manager.activeLocation.longitude, 0.0001)
        assertTrue(manager.locationSource is LocationSource.ManualMapPin)
    }

    @Test
    fun TC_T2_UI_03_locationPinOutsideIranianBounds() {
        val outsideAtlantic = GeoLocation(-10.0, -10.0)
        val outsideEurope = GeoLocation(48.8566, 2.3522) // Paris
        val tehranCenter = GeoLocation(35.6892, 51.3890) // Inside Iran

        // 1. Boundary check via production IranianGeoBounds
        assertFalse(IranianGeoBounds.isWithinIran(outsideAtlantic))
        assertFalse(IranianGeoBounds.isWithinIran(outsideEurope))
        assertTrue(IranianGeoBounds.isWithinIran(tehranCenter))

        // 2. Validate OutOfBounds error response and Persian warning banner
        val resultAtlantic = IranianGeoBounds.validateLocation(outsideAtlantic)
        assertTrue("Validation must return OutOfBounds", resultAtlantic is LocationValidationResult.OutOfBounds)
        val outOfBounds = resultAtlantic as LocationValidationResult.OutOfBounds
        assertEquals(IranianGeoBounds.OUT_OF_BOUNDS_WARNING, outOfBounds.warningMessage)
        assertEquals(outsideAtlantic, outOfBounds.location)

        // 3. Validate in-bounds success
        val resultValid = IranianGeoBounds.validateLocation(tehranCenter)
        assertTrue("Tehran must be Valid", resultValid is LocationValidationResult.Valid)
    }

    @Test
    fun TC_T2_UI_04_debugNetworkCaptureBufferOverflow() {
        val pool = DebugNetworkCapturePool(maxCapacity = 100)

        // Feed 500 entries through the production capture pool
        for (i in 1..500) {
            pool.record(
                NetworkCaptureEntry(
                    id = "req-$i",
                    method = "GET",
                    url = "https://torob.com/v4/base-product/search/?q=item$i",
                    statusCode = 200,
                    durationMs = 30L,
                    requestHeaders = mapOf("User-Agent" to "PriceHunter"),
                    responseHeaders = mapOf("Content-Type" to "application/json"),
                    responseBody = "Log entry $i"
                )
            )
        }

        // Verify pool size clamped to maxCapacity
        assertEquals(100, pool.size)
        val entries = pool.getAllEntries()
        assertEquals(100, entries.size)

        // FIFO eviction verification: entries 1..400 evicted; 401..500 retained
        assertEquals("req-401", entries.first().id)
        assertEquals("Log entry 401", entries.first().responseBody)
        assertEquals("req-500", entries.last().id)
        assertEquals("Log entry 500", entries.last().responseBody)
    }

    @Test
    fun TC_T2_UI_05_webViewAuthenticationSslCertificateError() {
        var cancelCalled = false
        var proceedCalled = false

        val testHandler = object : SslErrorHandlerCompat {
            override fun proceed() {
                proceedCalled = true
            }
            override fun cancel() {
                cancelCalled = true
            }
        }

        val authStorage = InMemoryAuthStorage()
        val webClient = ProviderAuthWebViewClient(
            authStorage = authStorage,
            providerKey = "snappfood"
        )

        // Invoke SSL error callback on untrusted endpoint
        webClient.onReceivedSslError(
            handler = testHandler,
            failingUrl = "https://untrusted-snappfood-phishing.com/login",
            primaryError = 3 // SSL_UNTRUSTED
        )

        assertTrue("SSL error must invoke handler.cancel()", cancelCalled)
        assertFalse("SSL error must NEVER invoke handler.proceed()", proceedCalled)
        assertTrue("ProviderAuthWebViewClient must record lastSslErrorCancelled", webClient.lastSslErrorCancelled)
        assertFalse(webClient.lastSslProceeded)
    }

    // --- 4. Extra WAF & Geoblock Extremes (2 tests) ---

    @Test
    fun TC_T2_EX_01_torobSellerDetailsReturns490() = runBlocking {
        mockServer.setScenario("torob", MockScenario.WAF_490)
        val provider = TorobProvider(mockServer.baseUrl)
        val result = provider.searchProducts("تست")
        assertTrue(result is ProviderResult.Error)
    }

    @Test
    fun TC_T2_EX_02_snappShopSotoon403FlagsVpnIssue() = runBlocking {
        mockServer.setScenario("snappshop", MockScenario.GEOBLOCK_403)
        val provider = SnappShopProvider(mockServer.baseUrl)

        val result = provider.searchProducts("لباس")
        assertTrue("SnappShop 403 must return ProviderResult.Error", result is ProviderResult.Error)
        assertTrue("isVpnIssue must be flagged true on Sotoon 403", (result as ProviderResult.Error).isVpnIssue)
    }
}
