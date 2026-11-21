package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SnappShopProviderTest {

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
        val provider = SnappShopProvider(mockServer.baseUrl)
        assertEquals("snappshop", provider.providerId)
        assertEquals("اسنپ‌شاپ", provider.displayName)
        assertEquals(ProviderType.SHOPPING, provider.providerType)
        assertTrue(provider.isEnabled)
    }

    @Test
    fun testSnappShopSearchWithCustomHeaders() = runBlocking {
        val provider = SnappShopProvider(mockServer.baseUrl)
        val result = provider.searchProducts("سامسونگ")
        assertTrue(result is ProviderResult.Success)
        val offers = (result as ProviderResult.Success).data
        assertTrue(offers.isNotEmpty())
        val first = offers.first()
        assertEquals(67900000L, first.priceToman)
        assertTrue(first.originalUrl.startsWith("https://snappshop.ir"))
    }

    @Test
    fun testSnappShopSotoonCdn403FlaggedAsVpnIssue() = runBlocking {
        mockServer.setScenario("snappshop", MockScenario.GEOBLOCK_403)
        val provider = SnappShopProvider(mockServer.baseUrl)
        val result = provider.searchProducts("تست")
        assertTrue(result is ProviderResult.Error)
        val error = result as ProviderResult.Error
        assertTrue("Sotoon 403 should be marked as VPN issue", error.isVpnIssue)
    }

    @Test
    fun testExplicitNullDiscountedPriceAndFieldsDoNotCrash() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val payload = """
        {
          "status": 200,
          "data": {
            "structure": [
              {
                "section_type": "plp",
                "items": [
                  {
                    "id": "snp-999",
                    "title": "کالای بدون تخفیف",
                    "price": {
                      "price": 100000,
                      "discounted_price": null
                    },
                    "state": {
                      "rate": null
                    },
                    "image": null,
                    "href": null
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(payload))
        server.start()

        try {
            val provider = SnappShopProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchProducts("تست")
            assertTrue("Expected success but got $result", result is ProviderResult.Success)
            val offers = (result as ProviderResult.Success).data
            assertEquals(1, offers.size)
            val offer = offers.first()
            assertEquals("snp-999", offer.id)
            assertEquals("کالای بدون تخفیف", offer.title)
            assertEquals(100000L, offer.priceToman)
            assertEquals(100000L, offer.rawPrice)
            assertNull(offer.ratingOutOf5)
            assertNull(offer.imageUrl)
            assertEquals("https://snappshop.ir/product/snp-snp-999", offer.originalUrl)
        } finally {
            server.shutdown()
        }
    }
}
