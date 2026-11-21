package com.teamwork.pricehunter.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RemoteConfigManagerTest {

    private class FakeAssetLoader(private val assets: Map<String, String>) : AssetLoader {
        override fun loadAsset(fileName: String): String =
            assets[fileName] ?: throw IllegalArgumentException("Asset not found: $fileName")
    }

    private class InMemoryConfigCacheStorage : ConfigCacheStorage {
        private var cache: String? = null
        private var etag: String? = null
        private var lastFetch: Long = 0L

        override fun readCache(): String? = cache
        override fun writeCache(json: String) { cache = json }
        override fun getEtag(): String? = etag
        override fun setEtag(etag: String?) { this.etag = etag }
        override fun getLastFetchTime(): Long = lastFetch
        override fun setLastFetchTime(timestamp: Long) { lastFetch = timestamp }
        override fun clear() { cache = null; etag = null; lastFetch = 0L }
    }

    private class MockInterceptor : Interceptor {
        var responseCode: Int = 200
        var responseBody: String = ""
        var responseEtag: String? = "etag-123"
        var shouldThrowException: Boolean = false

        override fun intercept(chain: Interceptor.Chain): Response {
            if (shouldThrowException) {
                throw IOException("Simulated network failure")
            }
            val responseBodyObj = responseBody.toResponseBody("application/json".toMediaTypeOrNull())
            val builder = Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("Mock response")
                .body(responseBodyObj)
            if (responseEtag != null) {
                builder.header("ETag", responseEtag!!)
            }
            return builder.build()
        }
    }

    private val defaultAssetJson = """
    {
      "version": 1,
      "providers": {
        "digikala": { "id": "digikala", "enabled": true, "timeoutMs": 10000, "baseUrl": "https://api.digikala.com/v2/" },
        "torob": { "id": "torob", "enabled": true, "timeoutMs": 10000, "baseUrl": "https://api.torob.com/v4/" },
        "snappshop": { "id": "snappshop", "enabled": true, "timeoutMs": 10000, "baseUrl": "https://api.snapp.market/" },
        "snappfood": { "id": "snappfood", "enabled": true, "timeoutMs": 12000, "baseUrl": "https://snappfood.ir/mobile/v2/" },
        "tapsifood": { "id": "tapsifood", "enabled": true, "timeoutMs": 12000, "baseUrl": "https://api.tapsi.food/" },
        "okala": { "id": "okala", "enabled": false, "timeoutMs": 10000, "baseUrl": "https://api.okala.com/" }
      }
    }
    """.trimIndent()

    private lateinit var assetLoader: FakeAssetLoader
    private lateinit var cacheStorage: InMemoryConfigCacheStorage
    private lateinit var mockInterceptor: MockInterceptor
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        assetLoader = FakeAssetLoader(mapOf("providers_config.json" to defaultAssetJson))
        cacheStorage = InMemoryConfigCacheStorage()
        mockInterceptor = MockInterceptor()
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(mockInterceptor)
            .build()
    }

    @Test
    fun testLoadDefaultFromAssets() {
        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient
        )

        val config = manager.getConfig()
        assertEquals(1, config.version)
        assertEquals(6, config.providers.size)
        assertTrue(config.providers.containsKey("digikala"))
        assertTrue(config.providers.containsKey("snappfood"))
        assertTrue(config.providers["snappfood"]!!.enabled)
        assertFalse(config.providers["okala"]!!.enabled)
    }

    @Test
    fun testRemoteUpdate200WithEtag() = runBlocking {
        val updatedJson = """
        {
          "version": 2,
          "providers": {
            "digikala": { "id": "digikala", "enabled": true, "timeoutMs": 15000, "baseUrl": "https://api.digikala.com/v3/" }
          }
        }
        """.trimIndent()

        mockInterceptor.responseCode = 200
        mockInterceptor.responseBody = updatedJson
        mockInterceptor.responseEtag = "v2-hash"

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient,
            remoteConfigUrl = "https://config.example.com/providers.json"
        )

        val refreshed = manager.refreshConfig(forceRefresh = true)
        assertEquals(2, refreshed.version)
        assertEquals(15000L, refreshed.providers["digikala"]?.timeoutMs)
        assertEquals("v2-hash", cacheStorage.getEtag())
        assertNotNull(cacheStorage.readCache())
    }

    @Test
    fun testRemoteUpdate304NotModified() = runBlocking {
        val cachedJson = """
        {
          "version": 3,
          "providers": {
            "torob": { "id": "torob", "enabled": true, "timeoutMs": 8000, "baseUrl": "https://api.torob.com/v4/" }
          }
        }
        """.trimIndent()

        cacheStorage.writeCache(cachedJson)
        cacheStorage.setEtag("v3-etag")

        mockInterceptor.responseCode = 304
        mockInterceptor.responseBody = ""

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient,
            remoteConfigUrl = "https://config.example.com/providers.json"
        )

        val config = manager.refreshConfig(forceRefresh = true)
        assertEquals(3, config.version)
        assertEquals(8000L, config.providers["torob"]?.timeoutMs)
    }

    @Test
    fun testRemoteUpdateErrorFallbackToCache() = runBlocking {
        val cachedJson = """
        {
          "version": 4,
          "providers": {
            "snappshop": { "id": "snappshop", "enabled": true, "timeoutMs": 9000, "baseUrl": "https://api.snapp.market/" }
          }
        }
        """.trimIndent()

        cacheStorage.writeCache(cachedJson)

        mockInterceptor.responseCode = 500
        mockInterceptor.responseBody = "Internal Server Error"

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient,
            remoteConfigUrl = "https://config.example.com/providers.json"
        )

        val config = manager.refreshConfig(forceRefresh = true)
        assertEquals(4, config.version)
        assertEquals("snappshop", config.providers["snappshop"]?.id)
    }

    @Test
    fun testRemoteUpdateErrorFallbackToAssets() = runBlocking {
        mockInterceptor.shouldThrowException = true

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient,
            remoteConfigUrl = "https://config.example.com/providers.json"
        )

        val config = manager.refreshConfig(forceRefresh = true)
        // Should fall back to bundled assets with 6 providers
        assertEquals(1, config.version)
        assertEquals(6, config.providers.size)
    }

    @Test
    fun testSchemaDriftTolerantParsing() = runBlocking {
        // Contains extra unknown fields (schema drift) and missing optional values
        val driftedJson = """
        {
          "version": 5,
          "new_feature_flags": { "ai_search": false },
          "deprecated_key": "some_value",
          "providers": {
            "digikala": {
              "id": "digikala",
              "enabled": true,
              "timeoutMs": 11000,
              "baseUrl": "https://api.digikala.com/v2/",
              "new_auth_scheme": "Bearer",
              "extra_params": { "client": "android" }
            }
          }
        }
        """.trimIndent()

        mockInterceptor.responseCode = 200
        mockInterceptor.responseBody = driftedJson

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient,
            remoteConfigUrl = "https://config.example.com/providers.json"
        )

        val config = manager.refreshConfig(forceRefresh = true)
        assertEquals(5, config.version)
        assertEquals(1, config.providers.size)
        assertEquals("digikala", config.providers["digikala"]?.id)
        assertEquals(11000L, config.providers["digikala"]?.timeoutMs)
    }

    @Test
    fun testColdInstanceWithValidUnexpiredDiskCacheDoesNotTriggerNetwork() = runBlocking {
        val cachedJson = """
        {
          "version": 6,
          "providers": {
            "snappfood": { "id": "snappfood", "enabled": true, "timeoutMs": 13000, "baseUrl": "https://snappfood.ir/mobile/v2/" }
          }
        }
        """.trimIndent()

        val now = System.currentTimeMillis()
        cacheStorage.writeCache(cachedJson)
        cacheStorage.setLastFetchTime(now - 3600 * 1000L)

        // If network is invoked, it would throw IOException
        mockInterceptor.shouldThrowException = true

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            okHttpClient = okHttpClient,
            remoteConfigUrl = "https://config.example.com/providers.json"
        )

        val config = manager.refreshConfig(forceRefresh = false)
        assertEquals(6, config.version)
        assertEquals("snappfood", config.providers["snappfood"]?.id)
        assertEquals(13000L, config.providers["snappfood"]?.timeoutMs)
    }
}
