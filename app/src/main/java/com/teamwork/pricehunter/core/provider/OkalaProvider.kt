package com.teamwork.pricehunter.core.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OkalaProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) : ShoppingProvider {

    override val providerId: String = "okala"
    override val displayName: String = "اکالا"
    override val providerType: ProviderType = ProviderType.GROCERY
    override var isEnabled: Boolean = true

    private val gson = Gson()

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.okala.com"

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

        val cleanBaseUrl = baseUrl.trimEnd('/')
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "$cleanBaseUrl/okala/search?q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PriceHunter/1.0 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProviderResult.Error("Okala HTTP ${response.code}", isVpnIssue = (response.code == 403))
                }
                val bodyStr = response.body?.string() ?: return@withContext ProviderResult.Success(emptyList())
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ProviderResult.Success(emptyList())
                }

                val data = json.getSafeArray("data") ?: return@withContext ProviderResult.Success(emptyList())

                val offers = mutableListOf<ShoppingOffer>()
                for (d in data) {
                    val dObj = d.asSafeObject() ?: continue
                    val id = dObj.getSafeString("id") ?: continue
                    val title = dObj.getSafeString("title") ?: ""
                    val price = dObj.getSafeLong("price") ?: 0L
                    val urlLink = dObj.getSafeString("url") ?: "https://okala.com/product/$id"

                    offers.add(
                        ShoppingOffer(
                            id = id,
                            providerName = displayName,
                            title = title,
                            rawPrice = price,
                            priceToman = price,
                            originalUrl = urlLink,
                            imageUrl = null,
                            sellerName = "اکالا",
                            ratingOutOf5 = 4.5f,
                            isAvailable = true
                        )
                    )
                }
                ProviderResult.Success(offers)
            }
        } catch (e: Exception) {
            ProviderResult.Error("Okala error: ${e.message}", cause = e)
        }
    }
}
