package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.core.security.InMemoryAuthStorage
import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DigikalaProviderTest {

    private lateinit var mockServer: MockServerManager
    private lateinit var authStorage: InMemoryAuthStorage

    @Before
    fun setUp() {
        mockServer = MockServerManager()
        mockServer.start()
        authStorage = InMemoryAuthStorage()
    }

    @After
    fun tearDown() {
        mockServer.stop()
    }

    @Test
    fun testProviderMetadata() {
        val provider = DigikalaProvider(mockServer.baseUrl)
        assertEquals("digikala", provider.providerId)
        assertEquals("دیجی‌کالا", provider.displayName)
        assertEquals(ProviderType.SHOPPING, provider.providerType)
        assertTrue(provider.isEnabled)
    }

    @Test
    fun testDisabledProviderReturnsEmpty() = runBlocking {
        val provider = DigikalaProvider(mockServer.baseUrl)
        provider.isEnabled = false
        val result = provider.searchProducts("گوشی")
        assertTrue(result is ProviderResult.Success)
        assertTrue((result as ProviderResult.Success).data.isEmpty())
    }

    @Test
    fun testBlankQueryReturnsEmpty() = runBlocking {
        val provider = DigikalaProvider(mockServer.baseUrl)
        val result = provider.searchProducts("   ")
        assertTrue(result is ProviderResult.Success)
        assertTrue((result as ProviderResult.Success).data.isEmpty())
    }

    @Test
    fun testCurrencyConversionRialToToman() = runBlocking {
        val provider = DigikalaProvider(mockServer.baseUrl, authStorage = authStorage)
        val result = provider.searchProducts("سامسونگ")
        assertTrue(result is ProviderResult.Success)
        val offers = (result as ProviderResult.Success).data
        assertTrue(offers.isNotEmpty())
        val first = offers.first()
        assertEquals(685000000L, first.rawPrice)
        assertEquals(68500000L, first.priceToman)
        assertEquals(first.rawPrice / 10L, first.priceToman)
        assertTrue(first.originalUrl.startsWith("https://www.digikala.com/product/dkp-"))
    }

    @Test
    fun testVpnGeoblockDetectionOn403() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.GEOBLOCK_403)
        val provider = DigikalaProvider(mockServer.baseUrl)
        val result = provider.searchProducts("سامسونگ")
        assertTrue(result is ProviderResult.Error)
        val error = result as ProviderResult.Error
        assertTrue("HTTP 403 should be marked as VPN issue", error.isVpnIssue)
    }

    @Test
    fun testExplicitNullFieldsDoNotCrash() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val payload = """
        {
          "status": 200,
          "data": {
            "products": [
              {
                "id": "12345",
                "title_fa": "کالای تست بدون امتیاز",
                "status": "marketable",
                "rating": null,
                "images": null,
                "default_variant": {
                  "price": {
                    "selling_price": 500000,
                    "rrp_price": null
                  },
                  "seller": null
                }
              }
            ]
          }
        }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(payload))
        server.start()

        try {
            val provider = DigikalaProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchProducts("تست")
            assertTrue("Expected success but got $result", result is ProviderResult.Success)
            val offers = (result as ProviderResult.Success).data
            assertEquals(1, offers.size)
            val offer = offers.first()
            assertEquals("12345", offer.id)
            assertNull(offer.ratingOutOf5)
            assertNull(offer.imageUrl)
            assertEquals("دیجی‌کالا", offer.sellerName)
            assertEquals(50000L, offer.priceToman)
        } finally {
            server.shutdown()
        }
    }
}
