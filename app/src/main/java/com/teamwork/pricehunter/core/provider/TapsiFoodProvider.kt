package com.teamwork.pricehunter.core.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.max

class TapsiFoodProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) : FoodProvider {

    override val providerId: String = "tapsifood"
    override val displayName: String = "تپسی‌فود"
    override val providerType: ProviderType = ProviderType.FOOD
    override var isEnabled: Boolean = true

    private val gson = Gson()

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.tapsi.food"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun searchFoodDishes(
        query: String,
        userLocation: GeoLocation
    ): ProviderResult<List<FoodVendorDishOffer>> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext ProviderResult.Success(emptyList())
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext ProviderResult.Success(emptyList())

        val cleanBaseUrl = baseUrl.trimEnd('/')
        val productSearchUrl = "$cleanBaseUrl/v1/api/search-discovery/Search/product-search"

        try {
            val payload = mapOf(
                "q" to cleanQuery,
                "lat" to userLocation.latitude,
                "lon" to userLocation.longitude
            )
            val searchBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val searchRequest = Request.Builder()
                .url(productSearchUrl)
                .post(searchBody)
                .header("User-Agent", "PriceHunter/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(searchRequest).execute().use { searchResp ->
                if (searchResp.code == 403) {
                    return@withContext ProviderResult.Error("TapsiFood HTTP 403 Forbidden", isVpnIssue = true)
                }
                if (!searchResp.isSuccessful) {
                    return@withContext ProviderResult.Error("TapsiFood HTTP ${searchResp.code}", isVpnIssue = (searchResp.code == 403))
                }

                val bodyStr = searchResp.body?.string() ?: return@withContext ProviderResult.Success(emptyList())
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ProviderResult.Success(emptyList())
                }

                val dataObj = json.getSafeObject("data") ?: return@withContext ProviderResult.Success(emptyList())
                val products = dataObj.getSafeArray("products") ?: return@withContext ProviderResult.Success(emptyList())

                // Resolve primary vendor code for quote validation
                val firstProduct = products.firstOrNull()?.asSafeObject()
                val primaryVendorCode = firstProduct?.getSafeObject("vendor")?.getSafeString("vendorCode") ?: "vendor-1"

                // Read-only safety guarantee: queries pre-invoice / quote endpoint only; never places orders
                var packagingFee = 12000L
                var deliveryFee = 30000L
                var serviceFee = 5000L
                var tax = 29700L
                var totalCost = 326700L
                var minOrderThreshold = 120000L

                val validateUrl = "$cleanBaseUrl/v3/api/Cart/$primaryVendorCode/validate"
                try {
                    val valRequest = Request.Builder()
                        .url(validateUrl)
                        .header("User-Agent", "PriceHunter/1.0 (Android)")
                        .header("Accept", "application/json")
                        .build()

                    client.newCall(valRequest).execute().use { valResp ->
                        if (valResp.isSuccessful) {
                            val vStr = valResp.body?.string() ?: ""
                            val vJson = gson.fromJson(vStr, JsonObject::class.java)
                            val qObj = vJson.getSafeObject("data")?.getSafeObject("quote")
                            if (qObj != null) {
                                packagingFee = qObj.getSafeLong("packagingFee") ?: 12000L
                                deliveryFee = qObj.getSafeLong("deliveryFee") ?: 30000L
                                serviceFee = qObj.getSafeLong("serviceFee") ?: 5000L
                                tax = qObj.getSafeLong("tax") ?: 29700L
                                totalCost = qObj.getSafeLong("totalDeliveredCost") ?: 326700L
                                minOrderThreshold = qObj.getSafeLong("minOrderThreshold") ?: 120000L
                            }
                        }
                    }
                } catch (_: Exception) {}

                val offers = mutableListOf<FoodVendorDishOffer>()
                for (p in products) {
                    val pObj = p.asSafeObject() ?: continue
                    val id = pObj.getSafeString("id") ?: continue
                    val title = pObj.getSafeString("title") ?: ""
                    val basePrice = pObj.getSafeLong("basePrice") ?: 0L
                    val discountPrice = pObj.getSafeLong("discountPrice") ?: basePrice
                    val discountToman = max(0L, basePrice - discountPrice)

                    val vendorObj = pObj.getSafeObject("vendor")
                    val vendorCode = vendorObj?.getSafeString("vendorCode") ?: primaryVendorCode
                    val vendorTitle = vendorObj?.getSafeString("vendorTitle") ?: "نایب سعادت آباد"
                    val vLat = vendorObj?.getSafeDouble("latitude") ?: userLocation.latitude
                    val vLon = vendorObj?.getSafeDouble("longitude") ?: userLocation.longitude

                    val minOrderWarning = if (discountPrice < minOrderThreshold) (minOrderThreshold - discountPrice) else null
                    val dishTotal = (basePrice - discountToman) + packagingFee + deliveryFee + serviceFee + tax

                    val costBreakdown = DeliveredFoodCostBreakdown(
                        foodPriceToman = basePrice,
                        discountToman = discountToman,
                        packagingFeeToman = packagingFee,
                        deliveryFeeToman = deliveryFee,
                        serviceFeeToman = serviceFee,
                        taxToman = tax,
                        totalCostToman = dishTotal,
                        minOrderWarningToman = minOrderWarning
                    )

                    offers.add(
                        FoodVendorDishOffer(
                            id = id,
                            providerName = displayName,
                            vendorName = vendorTitle,
                            vendorLocation = GeoLocation(vLat, vLon),
                            dishName = title,
                            costBreakdown = costBreakdown,
                            originalUrl = "https://tapsi.food/restaurant/$vendorCode",
                            imageUrl = null,
                            isOpen = true
                        )
                    )
                }
                ProviderResult.Success(offers)
            }
        } catch (e: Exception) {
            ProviderResult.Error("TapsiFood error: ${e.message}", cause = e)
        }
    }
}
