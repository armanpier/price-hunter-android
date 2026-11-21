package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OkalaProviderTest {

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
        val provider = OkalaProvider(mockServer.baseUrl)
        assertEquals("okala", provider.providerId)
        assertEquals("اکالا", provider.displayName)
        assertEquals(ProviderType.GROCERY, provider.providerType)
        assertTrue(provider.isEnabled)
    }

    @Test
    fun testOkalaProductSearch() = runBlocking {
        val provider = OkalaProvider(mockServer.baseUrl)
        val result = provider.searchProducts("شیر")
        assertTrue(result is ProviderResult.Success)
        val offers = (result as ProviderResult.Success).data
        assertEquals(1, offers.size)
        val item = offers.first()
        assertEquals("شیر کم چرب کاله ۱ لیتر", item.title)
        assertEquals(35000L, item.priceToman)
    }

    @Test
    fun testExplicitNullPriceAndFieldsHandledSafely() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val payload = """
        {
          "status": 200,
          "data": [
            {
              "id": "ok-100",
              "title": "پنیر بدون قیمت",
              "price": null,
              "url": null
            }
          ]
        }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(payload))
        server.start()

        try {
            val provider = OkalaProvider(baseUrl = server.url("/").toString().removeSuffix("/"))
            val result = provider.searchProducts("پنیر")
            assertTrue("Expected success but got $result", result is ProviderResult.Success)
            val offers = (result as ProviderResult.Success).data
            assertEquals(1, offers.size)
            val item = offers.first()
            assertEquals("ok-100", item.id)
            assertEquals("پنیر بدون قیمت", item.title)
            assertEquals(0L, item.priceToman)
            assertEquals("https://okala.com/product/ok-100", item.originalUrl)
        } finally {
            server.shutdown()
        }
    }
}
