package com.teamwork.pricehunter.core.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ProvidersConfig(
    val version: Int = 1,
    val providers: Map<String, ProviderMeta> = emptyMap()
)

data class ProviderMeta(
    val id: String = "",
    val enabled: Boolean = true,
    val timeoutMs: Long = 10000L,
    val baseUrl: String = "",
    val extraHeaders: Map<String, String> = emptyMap()
)

fun ProvidersConfig.sanitized(): ProvidersConfig {
    val sanitizedProviders = providers.mapValues { (key, meta) ->
        meta.sanitized(key)
    }
    return this.copy(
        version = if (version <= 0) 1 else version,
        providers = sanitizedProviders
    )
}

fun ProviderMeta.sanitized(fallbackId: String = ""): ProviderMeta {
    return this.copy(
        id = if (id.isBlank()) fallbackId else id,
        timeoutMs = if (timeoutMs <= 0L) 10000L else timeoutMs,
        baseUrl = baseUrl ?: "",
        extraHeaders = extraHeaders ?: emptyMap()
    )
}

interface AssetLoader {
    fun loadAsset(fileName: String): String
}

class AndroidAssetLoader(private val context: Context) : AssetLoader {
    override fun loadAsset(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }
}

interface ConfigCacheStorage {
    fun readCache(): String?
    fun writeCache(json: String)
    fun getEtag(): String?
    fun setEtag(etag: String?)
    fun getLastFetchTime(): Long
    fun setLastFetchTime(timestamp: Long)
    fun clear()
}

class FileConfigCacheStorage(private val cacheDir: File) : ConfigCacheStorage {
    private val configFile = File(cacheDir, "providers_config_cache.json")
    private val metaFile = File(cacheDir, "providers_config_cache.meta")

    override fun readCache(): String? {
        return if (configFile.exists()) configFile.readText() else null
    }

    override fun writeCache(json: String) {
        configFile.parentFile?.mkdirs()
        configFile.writeText(json)
    }

    override fun getEtag(): String? {
        if (!metaFile.exists()) return null
        return metaFile.readLines().firstOrNull()?.takeIf { it.isNotBlank() }
    }

    override fun setEtag(etag: String?) {
        val currentLines = if (metaFile.exists()) metaFile.readLines() else emptyList()
        val timestamp = currentLines.getOrNull(1) ?: "0"
        metaFile.writeText("${etag ?: ""}\n$timestamp")
    }

    override fun getLastFetchTime(): Long {
        if (!metaFile.exists()) return 0L
        val lines = metaFile.readLines()
        return lines.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    override fun setLastFetchTime(timestamp: Long) {
        val currentLines = if (metaFile.exists()) metaFile.readLines() else emptyList()
        val etag = currentLines.firstOrNull() ?: ""
        metaFile.writeText("$etag\n$timestamp")
    }

    override fun clear() {
        if (configFile.exists()) configFile.delete()
        if (metaFile.exists()) metaFile.delete()
    }
}

class RemoteConfigManager(
    private val assetLoader: AssetLoader,
    private val cacheStorage: ConfigCacheStorage,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val remoteConfigUrl: String? = null,
    private val cacheTtlMs: Long = 12 * 3600 * 1000L // 12 hours default
) {
    private val gson: Gson = GsonBuilder().create()
    private val assetFileName = "providers_config.json"

    @Volatile
    private var currentConfig: ProvidersConfig? = null

    constructor(
        context: Context,
        remoteConfigUrl: String? = null,
        cacheTtlMs: Long = 12 * 3600 * 1000L
    ) : this(
        assetLoader = AndroidAssetLoader(context),
        cacheStorage = FileConfigCacheStorage(context.cacheDir),
        remoteConfigUrl = remoteConfigUrl,
        cacheTtlMs = cacheTtlMs
    )

    fun getConfig(): ProvidersConfig {
        return currentConfig ?: synchronized(this) {
            currentConfig ?: loadInitialConfig().also { currentConfig = it }
        }
    }

    private fun loadInitialConfig(): ProvidersConfig {
        val cachedJson = cacheStorage.readCache()
        if (!cachedJson.isNullOrBlank()) {
            try {
                val parsed = gson.fromJson(cachedJson, ProvidersConfig::class.java)?.sanitized()
                if (parsed != null && parsed.providers.isNotEmpty()) {
                    return parsed
                }
            } catch (e: Exception) {
                // Ignore corrupted cache, fall back to assets
            }
        }
        return loadFromAssets()
    }

    fun loadFromAssets(): ProvidersConfig {
        return try {
            val json = assetLoader.loadAsset(assetFileName)
            val parsed = gson.fromJson(json, ProvidersConfig::class.java)?.sanitized()
            parsed ?: fallbackHardcodedConfig()
        } catch (e: Exception) {
            fallbackHardcodedConfig()
        }
    }

    suspend fun refreshConfig(forceRefresh: Boolean = false): ProvidersConfig = withContext(Dispatchers.IO) {
        val url = remoteConfigUrl ?: return@withContext getConfig()

        val now = System.currentTimeMillis()
        val lastFetch = cacheStorage.getLastFetchTime()
        val isExpired = (now - lastFetch) >= cacheTtlMs

        if (!forceRefresh && !isExpired) {
            if (currentConfig != null) {
                return@withContext currentConfig!!
            }
            val cachedJson = cacheStorage.readCache()
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val parsed = gson.fromJson(cachedJson, ProvidersConfig::class.java)?.sanitized()
                    if (parsed != null && parsed.providers.isNotEmpty()) {
                        synchronized(this@RemoteConfigManager) {
                            currentConfig = parsed
                        }
                        return@withContext parsed
                    }
                } catch (e: Exception) {
                    // Corrupted cache, proceed to network fetch below
                }
            }
        }

        try {
            val requestBuilder = Request.Builder().url(url)
            val cachedEtag = cacheStorage.getEtag()
            if (!cachedEtag.isNullOrBlank() && !forceRefresh) {
                requestBuilder.header("If-None-Match", cachedEtag)
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    304 -> {
                        cacheStorage.setLastFetchTime(now)
                    }
                    200 -> {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val parsed = gson.fromJson(bodyString, ProvidersConfig::class.java)?.sanitized()
                            if (parsed != null && parsed.providers.isNotEmpty()) {
                                cacheStorage.writeCache(bodyString)
                                cacheStorage.setEtag(response.header("ETag"))
                                cacheStorage.setLastFetchTime(now)
                                synchronized(this@RemoteConfigManager) {
                                    currentConfig = parsed
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Network timeout / DNS / connection failure: safely retain existing or asset config
        }

        getConfig()
    }

    private fun fallbackHardcodedConfig(): ProvidersConfig {
        return ProvidersConfig(
            version = 1,
            providers = mapOf(
                "digikala" to ProviderMeta("digikala", enabled = true, timeoutMs = 10000L, baseUrl = "https://api.digikala.com/v2/"),
                "torob" to ProviderMeta("torob", enabled = true, timeoutMs = 10000L, baseUrl = "https://api.torob.com/v4/"),
                "snappshop" to ProviderMeta("snappshop", enabled = true, timeoutMs = 10000L, baseUrl = "https://api.snapp.market/"),
                "snappfood" to ProviderMeta("snappfood", enabled = true, timeoutMs = 12000L, baseUrl = "https://snappfood.ir/mobile/v2/"),
                "tapsifood" to ProviderMeta("tapsifood", enabled = true, timeoutMs = 12000L, baseUrl = "https://api.tapsi.food/"),
                "okala" to ProviderMeta("okala", enabled = false, timeoutMs = 10000L, baseUrl = "https://api.okala.com/")
            )
        )
    }
}
