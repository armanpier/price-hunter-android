package com.teamwork.pricehunter.e2e.tier2_boundary

import com.teamwork.pricehunter.core.network.*
import com.teamwork.pricehunter.e2e.mock.MockScenario
import com.teamwork.pricehunter.e2e.mock.MockServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tier 2: Boundary & Corner Cases — RemoteConfigManager Boundaries (5 tests)
 * Spec reference: TC-T2-RC-01 through TC-T2-RC-05
 */
class Tier2RemoteConfigBoundaryTest {

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

    private class TestCacheStorage : ConfigCacheStorage {
        private var cached: String? = null
        private var storedEtag: String? = null
        private var lastFetch: Long = 0L

        override fun readCache(): String? = cached
        override fun writeCache(json: String) { cached = json }
        override fun getEtag(): String? = storedEtag
        override fun setEtag(etag: String?) { this.storedEtag = etag }
        override fun getLastFetchTime(): Long = lastFetch
        override fun setLastFetchTime(timestamp: Long) { this.lastFetch = timestamp }
        override fun clear() { cached = null; storedEtag = null; lastFetch = 0L }
    }

    @Test
    fun TC_T2_RC_01_missingAssetConfigFile() {
        val failingLoader = object : AssetLoader {
            override fun loadAsset(fileName: String): String {
                throw IOException("File not found: $fileName")
            }
        }
        val cache = TestCacheStorage()
        val manager = RemoteConfigManager(failingLoader, cache)

        val config = manager.getConfig()

        assertNotNull("Must return fallback configuration when asset is missing", config)
        assertTrue(config.providers.isNotEmpty())
        assertTrue(config.providers.containsKey("digikala"))
    }

    @Test
    fun TC_T2_RC_02_remoteHttp500Or404Error() = runBlocking {
        val assetLoader = object : AssetLoader {
            override fun loadAsset(fileName: String): String =
                """{"version": 1, "providers": {"digikala": {"id": "digikala"}}}"""
        }
        val cache = TestCacheStorage()
        // Non-existent route returns 404
        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cache,
            remoteConfigUrl = "${mockServer.baseUrl}/non_existent_config.json"
        )

        val config = manager.refreshConfig(forceRefresh = true)

        assertNotNull(config)
        assertEquals("Should retain valid asset config on HTTP 404", 1, config.version)
    }

    @Test
    fun TC_T2_RC_03_malformedJsonRemoteResponse() = runBlocking {
        mockServer.setScenario("digikala", MockScenario.GEOBLOCK_403) // Returns HTML
        val assetLoader = object : AssetLoader {
            override fun loadAsset(fileName: String): String =
                """{"version": 1, "providers": {"digikala": {"id": "digikala"}}}"""
        }
        val cache = TestCacheStorage()
        // Server returns HTML on 403 route
        val manager = RemoteConfigManager(
            assetLoader = assetLoader,
            cacheStorage = cache,
            remoteConfigUrl = "${mockServer.baseUrl}/discovery/api/v2/search"
        )

        val config = manager.refreshConfig(forceRefresh = true)

        assertNotNull(config)
        assertEquals("Should retain existing config upon receiving malformed JSON", 1, config.version)
    }

    @Test
    fun TC_T2_RC_04_emptyRemoteProvidersArray() = runBlocking {
        val assetLoader = object : AssetLoader {
            override fun loadAsset(fileName: String): String =
                """{"version": 1, "providers": {"digikala": {"id": "digikala"}}}"""
        }
        val cache = TestCacheStorage()
        cache.writeCache("""{"version": 5, "providers": {}}""")

        val manager = RemoteConfigManager(assetLoader, cache)
        val config = manager.getConfig()

        // When cached providers are empty, it falls back to assets
        assertNotNull(config)
        assertTrue("Providers must not be empty", config.providers.isNotEmpty())
    }

    @Test
    fun TC_T2_RC_05_negativeOrZeroVersionNumber() {
        val raw = ProvidersConfig(version = -1, providers = mapOf("test" to ProviderMeta("test")))
        val sanitized = raw.sanitized()

        assertEquals("Negative version must be sanitized to >= 1", 1, sanitized.version)

        val zeroVer = ProvidersConfig(version = 0, providers = mapOf("test" to ProviderMeta("test"))).sanitized()
        assertEquals(1, zeroVer.version)
    }
}
