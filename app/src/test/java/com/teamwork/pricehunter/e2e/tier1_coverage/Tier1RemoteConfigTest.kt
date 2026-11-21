package com.teamwork.pricehunter.e2e.tier1_coverage

import com.teamwork.pricehunter.core.network.*
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Tier 1: Feature Coverage — RemoteConfigManager (5 tests)
 * Spec reference: TC-T1-RC-01 through TC-T1-RC-05
 */
class Tier1RemoteConfigTest {

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

    private class InMemoryConfigCache : ConfigCacheStorage {
        private var cache: String? = null
        private var etag: String? = null
        private var lastFetch: Long = 0L

        override fun readCache(): String? = cache
        override fun writeCache(json: String) { cache = json }
        override fun getEtag(): String? = etag
        override fun setEtag(etag: String?) { this.etag = etag }
        override fun getLastFetchTime(): Long = lastFetch
        override fun setLastFetchTime(timestamp: Long) { this.lastFetch = timestamp }
        override fun clear() {
            cache = null
            etag = null
            lastFetch = 0L
        }
    }

    private class MockAssetLoader(
        private val json: String = """
            {
              "version": 1,
              "providers": {
                "digikala": {"id": "digikala", "enabled": true, "timeoutMs": 10000, "baseUrl": "https://api.digikala.com/v2/"},
                "torob": {"id": "torob", "enabled": true, "timeoutMs": 10000, "baseUrl": "https://api.torob.com/v4/"},
                "snappshop": {"id": "snappshop", "enabled": true, "timeoutMs": 10000, "baseUrl": "https://api.snapp.market/"},
                "snappfood": {"id": "snappfood", "enabled": true, "timeoutMs": 12000, "baseUrl": "https://snappfood.ir/mobile/v2/"},
                "tapsifood": {"id": "tapsifood", "enabled": true, "timeoutMs": 12000, "baseUrl": "https://api.tapsi.food/"}
              }
            }
        """.trimIndent()
    ) : AssetLoader {
        override fun loadAsset(fileName: String): String = json
    }

    @Test
    fun TC_T1_RC_01_loadBundledAssetConfiguration() {
        val assetLoader = MockAssetLoader()
        val cacheStorage = InMemoryConfigCache()
        val manager = RemoteConfigManager(assetLoader, cacheStorage)

        val config = manager.getConfig()

        assertEquals(1, config.version)
        assertEquals(5, config.providers.size)
        assertTrue(config.providers.containsKey("digikala"))
        assertTrue(config.providers.containsKey("torob"))
    }

    @Test
    fun TC_T1_RC_02_remoteHttpConfigurationUpdate() = runBlocking {
        val assetLoader = MockAssetLoader()
        val cacheStorage = InMemoryConfigCache()
        val remoteUrl = "${mockServer.baseUrl}/remote-config/providers_config.json"
        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            remoteConfigUrl = remoteUrl
        )

        val updated = manager.refreshConfig(forceRefresh = true)

        assertEquals("Updated config should be version 2 from remote server", 2, updated.version)
        assertTrue(updated.providers.containsKey("snappfood"))
        assertNotNull(cacheStorage.readCache())
    }

    @Test
    fun TC_T1_RC_03_etagCaching304Handling() = runBlocking {
        val assetLoader = MockAssetLoader()
        val cacheStorage = InMemoryConfigCache()
        val remoteUrl = "${mockServer.baseUrl}/remote-config/providers_config.json"
        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            remoteConfigUrl = remoteUrl
        )

        // First fetch gets 200 and saves ETag
        val firstConfig = manager.refreshConfig(forceRefresh = true)
        assertEquals(2, firstConfig.version)
        val savedEtag = cacheStorage.getEtag()
        assertNotNull(savedEtag)

        // Second fetch with ETag receives 304 Not Modified
        val secondConfig = manager.refreshConfig(forceRefresh = true)
        assertEquals(2, secondConfig.version)
    }

    @Test
    fun TC_T1_RC_04_cacheExpiryWindowEnforcement() = runBlocking {
        val assetLoader = MockAssetLoader()
        val cacheStorage = InMemoryConfigCache()
        // Simulate fetch done 1 hour ago (TTL is 12 hours)
        cacheStorage.setLastFetchTime(System.currentTimeMillis() - 3600 * 1000L)
        cacheStorage.writeCache("""{"version": 3, "providers": {"okala": {"id": "okala"}}}""")

        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            remoteConfigUrl = "${mockServer.baseUrl}/remote-config/providers_config.json",
            cacheTtlMs = 12 * 3600 * 1000L
        )

        manager.getConfig()
        // Without forceRefresh and within TTL, does not issue network request
        val config = manager.refreshConfig(forceRefresh = false)
        assertEquals("Should retain unexpired cached version", 3, config.version)
    }

    @Test
    fun TC_T1_RC_05_schemaDriftResilience() = runBlocking {
        // Fixture contains unknown field "experimental_ai_v2": true
        val assetLoader = MockAssetLoader()
        val cacheStorage = InMemoryConfigCache()
        val remoteUrl = "${mockServer.baseUrl}/remote-config/providers_config.json"
        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cacheStorage,
            remoteConfigUrl = remoteUrl
        )

        val config = manager.refreshConfig(forceRefresh = true)

        assertNotNull(config)
        assertEquals(2, config.version)
        assertTrue(config.providers.isNotEmpty())
    }
}
