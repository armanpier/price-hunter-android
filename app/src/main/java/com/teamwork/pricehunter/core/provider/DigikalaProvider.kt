package com.teamwork.pricehunter.core.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.teamwork.pricehunter.core.security.AuthStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DigikalaProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
    private val authStorage: AuthStorage? = null
) : ShoppingProvider {

    override val providerId: String = "digikala"
    override val displayName: String = "دیجی‌کالا"
    override val providerType: ProviderType = ProviderType.SHOPPING
    override var isEnabled: Boolean = true

    private val gson = Gson()

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.digikala.com"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    override suspend fun searchProducts(query: String): ProviderResult<List<ShoppingOffer>> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext ProviderResult.Success(emptyList())
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext ProviderResult.Success(emptyList())

        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val url = "$cleanBaseUrl/discovery/api/v2/search?q=$encodedQuery&page=1"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "PriceHunter/1.0 (Android)")
            .header("Accept", "application/json")

        authStorage?.getCookies("digikala")?.let { cookie ->
            if (cookie.isNotBlank()) {
                requestBuilder.header("Cookie", cookie)
            }
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val setCookies = response.headers("Set-Cookie")
                for (sc in setCookies) {
                    if (sc.contains("digicdn_cookie")) {
                        authStorage?.saveCookies("digikala", sc)
                    }
                }

                if (response.code == 403) {
                    return@withContext ProviderResult.Error("Digikala HTTP 403 Forbidden", isVpnIssue = true)
                }
                if (!response.isSuccessful) {
                    return@withContext ProviderResult.Error("Digikala HTTP ${response.code}", isVpnIssue = (response.code == 403))
                }

                val bodyStr = response.body?.string() ?: return@withContext ProviderResult.Success(emptyList())
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ProviderResult.Success(emptyList())
                }

                val offers = mutableListOf<ShoppingOffer>()
                val dataObj = json.getSafeObject("data") ?: return@withContext ProviderResult.Success(emptyList())

                val directProducts = dataObj.getSafeArray("products")
                if (directProducts != null) {
                    for (p in directProducts) {
                        val pObj = p.asSafeObject() ?: continue
                        parseProductObject(pObj)?.let { offers.add(it) }
                    }
                }

                val widgets = dataObj.getSafeArray("widgets")
                if (widgets != null) {
                    for (w in widgets) {
                        val wObj = w.asSafeObject() ?: continue
                        if (wObj.getSafeString("type") == "vertical_product_listing") {
                            val subWidgets = wObj.getSafeObject("data")?.getSafeArray("widgets") ?: continue
                            for (pw in subWidgets) {
                                val pwObj = pw.asSafeObject() ?: continue
                                val prodObj = pwObj.getSafeObject("data") ?: continue
                                parseProductObject(prodObj)?.let { offers.add(it) }
                            }
                        }
                    }
                }

                ProviderResult.Success(offers)
            }
        } catch (e: Exception) {
            ProviderResult.Error("Digikala error: ${e.message}", cause = e)
        }
    }

    private fun parseProductObject(prodObj: JsonObject): ShoppingOffer? {
        val id = prodObj.getSafeString("id") ?: return null
        val title = prodObj.getSafeString("title_fa")
            ?: prodObj.getSafeString("title")
            ?: ""
        val status = prodObj.getSafeString("status") ?: ""
        if (status != "marketable") return null

        val variant = prodObj.getSafeObject("default_variant")
        val priceObj = variant?.getSafeObject("price")
        val rawRial = priceObj?.getSafeLong("selling_price")
            ?: priceObj?.getSafeLong("rrp_price")
            ?: return null
        val priceToman = rawRial / 10L // Convert IRR to IRT

        val sellerTitle = variant?.getSafeObject("seller")?.getSafeString("title") ?: "دیجی‌کالا"
        val ratingRate = prodObj.getSafeObject("rating")?.getSafeFloat("rate")
        val ratingOutOf5 = ratingRate?.let { it / 20.0f } // 0-100 scale to 0-5

        val images = prodObj.getSafeObject("images")?.getSafeObject("main")?.getSafeArray("url")
        val imageUrl = images?.firstOrNull()?.asSafeString()

        return ShoppingOffer(
            id = id,
            providerName = displayName,
            title = title,
            rawPrice = rawRial,
            priceToman = priceToman,
            originalUrl = "https://www.digikala.com/product/dkp-$id/",
            imageUrl = imageUrl,
            sellerName = sellerTitle,
            ratingOutOf5 = ratingOutOf5,
            isAvailable = true
        )
    }
}
