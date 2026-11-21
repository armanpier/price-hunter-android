package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TapsiFoodProviderTest {

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
        val provider = TapsiFoodProvider(mockServer.baseUrl)
        assertEquals("tapsifood", provider.providerId)
        assertEquals("تپسی‌فود", provider.displayName)
        assertEquals(ProviderType.FOOD, provider.providerType)
        assertTrue(provider.isEnabled)
    }

    @Test
    fun testTapsiFoodQuoteBreakdown() = runBlocking {
        val provider = TapsiFoodProvider(mockServer.baseUrl)
        val location = GeoLocation(35.7731, 51.4184)
        val result = provider.searchFoodDishes("کباب", location)

        assertTrue(result is ProviderResult.Success)
        val dishes = (result as ProviderResult.Success).data
        assertTrue(dishes.isNotEmpty())

        val dish = dishes.first()
        val cost = dish.costBreakdown
        assertEquals(275000L, cost.foodPriceToman)
        assertEquals(25000L, cost.discountToman)
        assertEquals(12000L, cost.packagingFeeToman)
        assertEquals(30000L, cost.deliveryFeeToman)
        assertEquals(5000L, cost.serviceFeeToman)
        assertEquals(29700L, cost.taxToman)
        assertEquals(326700L, cost.totalCostToman)
        assertTrue(dish.originalUrl.startsWith("https://tapsi.food/restaurant/"))
    }

    @Test
    fun testExplicitNullFieldsInProductAndQuoteHandledSafely() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val searchPayload = """
        {
          "status": 200,
          "data": {
            "products": [
              {
                "id": "tapsi-dish-null",
                "title": "کباب بدون قیمت",
                "basePrice": null,
                "discountPrice": null,
                "vendor": null
              }
            ]
          }
        }
        """.trimIndent()
        val validatePayload = """
        {
          "status": 200,
          "data": {
            "quote": {
              "packagingFee": null,
              "deliveryFee": null,
              "serviceFee": null,
              "tax": null,
              "totalDeliveredCost": null,
              "minOrderThreshold": null
            }
          }
        }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(searchPayload))
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(validatePayload))
        server.start()

        try {
            val provider = TapsiFoodProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchFoodDishes("کباب", GeoLocation(35.7, 51.4))
            assertTrue("Expected success but got $result", result is ProviderResult.Success)
            val dishes = (result as ProviderResult.Success).data
            assertEquals(1, dishes.size)
            val dish = dishes.first()
            assertEquals("tapsi-dish-null", dish.id)
            assertEquals("کباب بدون قیمت", dish.dishName)
            assertEquals(0L, dish.costBreakdown.foodPriceToman)
            assertEquals(0L, dish.costBreakdown.discountToman)
            assertEquals("نایب سعادت آباد", dish.vendorName)
        } finally {
            server.shutdown()
        }
    }
}
