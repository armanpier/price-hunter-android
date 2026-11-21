package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TorobProviderTest {

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
        val provider = TorobProvider(mockServer.baseUrl)
        assertEquals("torob", provider.providerId)
        assertEquals("ترب", provider.displayName)
        assertEquals(ProviderType.SHOPPING, provider.providerType)
        assertTrue(provider.isEnabled)
    }

    @Test
    fun testTorobSearchAndDirectPriceParsing() = runBlocking {
        val provider = TorobProvider(mockServer.baseUrl)
        val result = provider.searchProducts("آیفون")
        assertTrue(result is ProviderResult.Success)
        val offers = (result as ProviderResult.Success).data
        assertTrue(offers.isNotEmpty())
        val first = offers.first()
        assertEquals(61500000L, first.priceToman)
        assertEquals(first.priceToman, first.rawPrice) // Native Toman
        assertTrue(first.originalUrl.startsWith("https://torob.com/p/"))
    }

    @Test
    fun testTorobHttp490ArCaptchaHandledGracefully() = runBlocking {
        mockServer.setScenario("torob", MockScenario.WAF_490)
        val provider = TorobProvider(mockServer.baseUrl)
        val result = provider.searchProducts("تست")
        assertTrue(result is ProviderResult.Error)
        val error = result as ProviderResult.Error
        assertTrue(error.message.contains("490") || error.message.contains("CAPTCHA"))
        assertFalse("490 CAPTCHA should not be flagged as VPN issue", error.isVpnIssue)
    }

    @Test
    fun testExplicitNullPriceAndFieldsHandledGracefully() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val payload = """
        {
          "count": 1,
          "results": [
            {
              "random_key": "test_key_null_price",
              "name1": "کالای بدون قیمت",
              "name2": null,
              "price": null,
              "image_url": null,
              "shop_text": null
            }
          ]
        }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(payload))
        server.start()

        try {
            val provider = TorobProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchProducts("تست")
            assertTrue("Expected success but got $result", result is ProviderResult.Success)
            val offers = (result as ProviderResult.Success).data
            assertEquals(1, offers.size)
            val offer = offers.first()
            assertEquals("test_key_null_price", offer.id)
            assertEquals("کالای بدون قیمت", offer.title)
            assertEquals(0L, offer.priceToman)
            assertNull(offer.imageUrl)
            assertNull(offer.sellerName)
        } finally {
            server.shutdown()
        }
    }
}
