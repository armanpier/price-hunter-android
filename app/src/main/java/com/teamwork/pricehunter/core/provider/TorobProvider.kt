package com.teamwork.pricehunter.core.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TorobProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) : ShoppingProvider {

    override val providerId: String = "torob"
    override val displayName: String = "ترب"
    override val providerType: ProviderType = ProviderType.SHOPPING
    override var isEnabled: Boolean = true

    private val gson = Gson()

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.torob.com"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun searchProducts(query: String): ProviderResult<List<ShoppingOffer>> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext ProviderResult.Success(emptyList())
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext ProviderResult.Success(emptyList())

        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val cleanBaseUrl = baseUrl.trimEnd('/')
        // ArCaptcha 490 bypass: querying base-product search with page=0 and size=24
        // returns the lowest pre-aggregated price card directly, avoiding /v4/base-product/sellers
        val url = "$cleanBaseUrl/v4/base-product/search/?q=$encodedQuery&page=0&size=24"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PriceHunter/1.0 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 490) {
                    return@withContext ProviderResult.Error("Torob rate limit / CAPTCHA challenge (490)", isVpnIssue = false)
                }
                if (response.code == 403) {
                    return@withContext ProviderResult.Error("Torob HTTP 403 Forbidden", isVpnIssue = true)
                }
                if (!response.isSuccessful) {
                    return@withContext ProviderResult.Error("Torob HTTP ${response.code}")
                }

                val bodyStr = response.body?.string() ?: return@withContext ProviderResult.Success(emptyList())
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ProviderResult.Success(emptyList())
                }

                val results = json.getSafeArray("results") ?: return@withContext ProviderResult.Success(emptyList())
                val offers = mutableListOf<ShoppingOffer>()

                for (r in results) {
                    val rObj = r.asSafeObject() ?: continue
                    val randomKey = rObj.getSafeString("random_key") ?: continue
                    val title = rObj.getSafeString("name1") ?: rObj.getSafeString("name2") ?: ""
                    val priceToman = rObj.getSafeLong("price") ?: 0L
                    val imageUrl = rObj.getSafeString("image_url")
                    val shopText = rObj.getSafeString("shop_text")

                    offers.add(
                        ShoppingOffer(
                            id = randomKey,
                            providerName = displayName,
                            title = title,
                            rawPrice = priceToman,
                            priceToman = priceToman,
                            originalUrl = "https://torob.com/p/$randomKey/",
                            imageUrl = imageUrl,
                            sellerName = shopText,
                            ratingOutOf5 = null,
                            isAvailable = true
                        )
                    )
                }
                ProviderResult.Success(offers)
            }
        } catch (e: Exception) {
            ProviderResult.Error("Torob error: ${e.message}", cause = e)
        }
    }
}
