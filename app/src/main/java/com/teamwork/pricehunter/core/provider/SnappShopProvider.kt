package com.teamwork.pricehunter.core.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SnappShopProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) : ShoppingProvider {

    override val providerId: String = "snappshop"
    override val displayName: String = "اسنپ‌شاپ"
    override val providerType: ProviderType = ProviderType.SHOPPING
    override var isEnabled: Boolean = true

    private val gson = Gson()

    companion object {
        const val DEFAULT_BASE_URL: String = "https://apix.snappshop.ir"

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
        val url = "$cleanBaseUrl/search/v1"
        val jsonPayload = gson.toJson(mapOf("q" to cleanQuery))
        val jsonBody = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(jsonBody)
            .header("s-device", "DESKTOP")
            .header("s-device-source", "shop")
            .header("s-area-ids", "29,197")
            .header("x-origin", "https://snappshop.ir")
            .header("Accept", "application/json")
            .header("User-Agent", "PriceHunter/1.0 (Android)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    return@withContext ProviderResult.Error("SnappShop Sotoon CDN 403 Forbidden", isVpnIssue = true)
                }
                if (!response.isSuccessful) {
                    return@withContext ProviderResult.Error("SnappShop HTTP ${response.code}", isVpnIssue = (response.code == 403))
                }

                val bodyStr = response.body?.string() ?: return@withContext ProviderResult.Success(emptyList())
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ProviderResult.Success(emptyList())
                }

                val dataObj = json.getSafeObject("data") ?: return@withContext ProviderResult.Success(emptyList())
                val structure = dataObj.getSafeArray("structure") ?: return@withContext ProviderResult.Success(emptyList())

                val offers = mutableListOf<ShoppingOffer>()
                for (s in structure) {
                    val sObj = s.asSafeObject() ?: continue
                    if (sObj.getSafeString("section_type") == "plp") {
                        val items = sObj.getSafeArray("items") ?: continue
                        for (item in items) {
                            val itObj = item.asSafeObject() ?: continue
                            val id = itObj.getSafeString("id") ?: continue
                            val title = itObj.getSafeString("title") ?: ""
                            val priceObj = itObj.getSafeObject("price")
                            val discPrice = priceObj?.getSafeLong("discounted_price")
                            val basePrice = priceObj?.getSafeLong("price") ?: 0L
                            val priceToman = discPrice ?: basePrice

                            val stateObj = itObj.getSafeObject("state")
                            val rate = stateObj?.getSafeFloat("rate")
                            val image = itObj.getSafeString("image")
                            val href = itObj.getSafeString("href") ?: "/product/snp-$id"
                            val originalUrl = if (href.startsWith("http")) href else "https://snappshop.ir$href"

                            offers.add(
                                ShoppingOffer(
                                    id = id,
                                    providerName = displayName,
                                    title = title,
                                    rawPrice = basePrice,
                                    priceToman = priceToman,
                                    originalUrl = originalUrl,
                                    imageUrl = image,
                                    sellerName = null,
                                    ratingOutOf5 = rate,
                                    isAvailable = true
                                )
                            )
                        }
                    }
                }
                ProviderResult.Success(offers)
            }
        } catch (e: Exception) {
            ProviderResult.Error("SnappShop error: ${e.message}", cause = e)
        }
    }
}
