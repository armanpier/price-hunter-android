package com.teamwork.pricehunter.data.repository

import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.provider.*
import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchEngineRepositoryTest {

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

    @Test
    fun testProviderRegistration() {
        val repo = SearchEngineRepository()
        val digikala = DigikalaProvider(mockServer.baseUrl)
        val snappFood = SnappFoodProvider(mockServer.baseUrl)

        repo.registerShoppingProvider(digikala)
        repo.registerFoodProvider(snappFood)

        assertEquals(1, repo.getShoppingProviders().size)
        assertEquals(1, repo.getFoodProviders().size)
        assertEquals("digikala", repo.getShoppingProviders()[0].providerId)
        assertEquals("snappfood", repo.getFoodProviders()[0].providerId)
    }

    @Test
    fun testParallelShoppingSearch() = runBlocking {
        val digikala = DigikalaProvider(mockServer.baseUrl)
        val torob = TorobProvider(mockServer.baseUrl)
        val snappshop = SnappShopProvider(mockServer.baseUrl)
        val repo = SearchEngineRepository(mutableListOf(digikala, torob, snappshop))

        val start = System.currentTimeMillis()
        val results = repo.searchShoppingParallel("سامسونگ")
        val elapsed = System.currentTimeMillis() - start

        assertTrue(results.isNotEmpty())
        assertTrue("Execution should be fast and concurrent (<3000ms)", elapsed < 3000)

        // Verify strictly ascending sort
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].priceToman <= results[i + 1].priceToman)
        }
    }

    @Test
    fun testErrorIsolationSingleProviderFailure() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.SERVER_ERROR_500)
        mockServer.setScenario("torob", MockScenario.NORMAL)
        mockServer.setScenario("snappshop", MockScenario.NORMAL)

        val digikala = DigikalaProvider(mockServer.baseUrl)
        val torob = TorobProvider(mockServer.baseUrl)
        val snappshop = SnappShopProvider(mockServer.baseUrl)
        val repo = SearchEngineRepository(mutableListOf(digikala, torob, snappshop))

        val results = repo.searchShoppingParallel("سامسونگ")
        assertTrue("Results should still be returned from Torob & SnappShop", results.isNotEmpty())
        assertTrue(results.any { it.providerName == "ترب" })
        assertTrue(results.any { it.providerName == "اسنپ‌شاپ" })
    }

    @Test
    fun testShoppingFlowEmitsProgressively() = runBlocking {
        val digikala = DigikalaProvider(mockServer.baseUrl)
        val torob = TorobProvider(mockServer.baseUrl)
        val snappshop = SnappShopProvider(mockServer.baseUrl)
        val repo = SearchEngineRepository(mutableListOf(digikala, torob, snappshop))

        val emissions = repo.searchShoppingFlow("سامسونگ").take(2).toList()
        assertTrue("Flow should emit results", emissions.isNotEmpty())
        assertTrue("Emitted list should have offers", emissions[0].isNotEmpty())
    }

    @Test
    fun testParallelFoodSearchSortedByTotalCost() = runBlocking {
        val snappfood = SnappFoodProvider(mockServer.baseUrl)
        val tapsifood = TapsiFoodProvider(mockServer.baseUrl)
        val repo = SearchEngineRepository(foodProviders = mutableListOf(snappfood, tapsifood))

        val dishes = repo.searchFoodParallel("کباب", GeoLocation(35.7733, 51.4185))
        assertTrue(dishes.isNotEmpty())

        for (i in 0 until dishes.size - 1) {
            assertTrue(
                "Dishes must be sorted ascending by totalCostToman",
                dishes[i].costBreakdown.totalCostToman <= dishes[i + 1].costBreakdown.totalCostToman
            )
        }
    }
}
