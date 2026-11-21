package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SnappFoodProviderTest {

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
    fun testProviderMetadata() {
        val provider = SnappFoodProvider(mockServer.baseUrl)
        assertEquals("snappfood", provider.providerId)
        assertEquals("اسنپ‌فود", provider.displayName)
        assertEquals(ProviderType.FOOD, provider.providerType)
        assertTrue(provider.isEnabled)
    }

    @Test
    fun testSnappFoodCostBreakdownCalculation() = runBlocking {
        val provider = SnappFoodProvider(mockServer.baseUrl)
        val location = GeoLocation(35.7733, 51.4185)
        val result = provider.searchFoodDishes("پیتزا", location)

        assertTrue(result is ProviderResult.Success)
        val dishes = (result as ProviderResult.Success).data
        assertTrue(dishes.isNotEmpty())

        val dish = dishes.first()
        val cost = dish.costBreakdown
        // Expected formula verification
        val expectedSubtotal = cost.foodPriceToman - cost.discountToman
        val expectedNetDelivery = cost.deliveryFeeToman
        val expectedTotal = expectedSubtotal + cost.packagingFeeToman + expectedNetDelivery + cost.serviceFeeToman + cost.taxToman
        assertEquals(expectedTotal, cost.totalCostToman)
        assertTrue(dish.originalUrl.startsWith("https://snappfood.ir/restaurant/menu/"))
    }

    @Test
    fun testSnappFood403FlaggedAsVpnIssue() = runBlocking {
        mockServer.setScenario("snappfood", MockScenario.GEOBLOCK_403)
        val provider = SnappFoodProvider(mockServer.baseUrl)
        val result = provider.searchFoodDishes("پیتزا", GeoLocation(35.7, 51.4))
        assertTrue(result is ProviderResult.Error)
        val error = result as ProviderResult.Error
        assertTrue(error.isVpnIssue)
    }

    @Test
    fun testExplicitNullFieldsInDishAndVendorHandledSafely() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val dishPayload = """
        {
          "status": true,
          "data": {
            "items": [
              {
                "id": "dish-null-1",
                "title": "غذای تست",
                "price": null,
                "discountRatio": null,
                "vendor": null
              }
            ]
          }
        }
        """.trimIndent()
        val statePayload = """
        {
          "status": true,
          "data": {
            "delivery": {
              "fee": null,
              "discount": null
            },
            "packaging_fee": null,
            "tax_percent": null,
            "minOrder": null
          }
        }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(dishPayload))
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(statePayload))
        server.start()

        try {
            val provider = SnappFoodProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchFoodDishes("تست", GeoLocation(35.7, 51.4))
            assertTrue("Expected success but got $result", result is ProviderResult.Success)
            val dishes = (result as ProviderResult.Success).data
            assertEquals(1, dishes.size)
            val dish = dishes.first()
            assertEquals("dish-null-1", dish.id)
            assertEquals("غذای تست", dish.dishName)
            assertEquals(0L, dish.costBreakdown.foodPriceToman)
            assertEquals(0L, dish.costBreakdown.discountToman)
            assertEquals("رستوران", dish.vendorName)
        } finally {
            server.shutdown()
        }
    }
}
