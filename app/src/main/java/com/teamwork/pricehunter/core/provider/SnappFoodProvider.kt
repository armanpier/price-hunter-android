package com.teamwork.pricehunter.core.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class SnappFoodProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) : FoodProvider {

    override val providerId: String = "snappfood"
    override val displayName: String = "اسنپ‌فود"
    override val providerType: ProviderType = ProviderType.FOOD
    override var isEnabled: Boolean = true

    private val gson = Gson()

    companion object {
        const val DEFAULT_BASE_URL: String = "https://snappfood.ir"

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
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val dishUrl = "$cleanBaseUrl/search/api/v5/search?lat=${userLocation.latitude}&long=${userLocation.longitude}&query=$encodedQuery"
        val stateUrl = "$cleanBaseUrl/mobile/v2/restaurant/details/state"

        try {
            val dishRequest = Request.Builder()
                .url(dishUrl)
                .header("User-Agent", "PriceHunter/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(dishRequest).execute().use { dishResp ->
                if (dishResp.code == 403) {
                    return@withContext ProviderResult.Error("SnappFood 403 Forbidden", isVpnIssue = true)
                }
                if (!dishResp.isSuccessful) {
                    return@withContext ProviderResult.Error("SnappFood dish HTTP ${dishResp.code}", isVpnIssue = (dishResp.code == 403))
                }

                val dishBody = dishResp.body?.string() ?: return@withContext ProviderResult.Success(emptyList())
                val dishJson = try {
                    gson.fromJson(dishBody, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ProviderResult.Success(emptyList())
                }

                val dataObj = dishJson.getSafeObject("data")
                val items = dataObj?.getSafeObject("product_variations")?.getSafeArray("items")
                    ?: dataObj?.getSafeArray("items")
                    ?: return@withContext ProviderResult.Success(emptyList())

                // Fetch dynamic state for packaging, tax, delivery fee
                var deliveryFee = 30000L
                var packagingFee = 15000L
                var taxPercent = 10L
                var minOrder = 150000L

                try {
                    val stateRequest = Request.Builder()
                        .url(stateUrl)
                        .header("User-Agent", "PriceHunter/1.0 (Android)")
                        .header("Accept", "application/json")
                        .build()

                    client.newCall(stateRequest).execute().use { stateResp ->
                        if (stateResp.isSuccessful) {
                            val stateBody = stateResp.body?.string() ?: ""
                            val stateJson = gson.fromJson(stateBody, JsonObject::class.java)
                            val stateData = stateJson.getSafeObject("data")
                            if (stateData != null) {
                                val del = stateData.getSafeObject("delivery")
                                val baseFee = del?.getSafeLong("fee") ?: 30000L
                                val delDiscount = del?.getSafeLong("discount") ?: 0L
                                deliveryFee = max(0L, baseFee - delDiscount)
                                packagingFee = stateData.getSafeLong("packaging_fee") ?: 15000L
                                taxPercent = stateData.getSafeLong("tax_percent") ?: 10L
                                minOrder = stateData.getSafeLong("minOrder") ?: 150000L
                            }
                        }
                    }
                } catch (_: Exception) {}

                val offers = mutableListOf<FoodVendorDishOffer>()
                for (it in items) {
                    val itObj = it.asSafeObject() ?: continue
                    val id = itObj.getSafeString("id") ?: continue
                    val dishName = itObj.getSafeString("title") ?: ""
                    val foodPrice = itObj.getSafeLong("price") ?: 0L
                    val discountRatio = itObj.getSafeLong("discountRatio") ?: 0L
                    val rawDiscount = (foodPrice * min(100L, max(0L, discountRatio))) / 100L
                    val discountToman = min(foodPrice, rawDiscount)

                    val vendorObj = itObj.getSafeObject("vendor")
                    val vendorCode = vendorObj?.getSafeString("code") ?: "vendor-1"
                    val vendorTitle = vendorObj?.getSafeString("title") ?: "رستوران"
                    val vLat = vendorObj?.getSafeDouble("lat") ?: userLocation.latitude
                    val vLon = vendorObj?.getSafeDouble("lon") ?: userLocation.longitude

                    val costBreakdown = FoodCostCalculator.calculateDeliveredCost(
                        foodPriceToman = foodPrice,
                        discountToman = discountToman,
                        packagingFeeToman = packagingFee,
                        deliveryFeeToman = deliveryFee,
                        deliveryDiscountToman = 0L,
                        serviceFeeToman = 0L,
                        taxPercent = taxPercent,
                        minOrderThresholdToman = minOrder
                    )

                    offers.add(
                        FoodVendorDishOffer(
                            id = id,
                            providerName = displayName,
                            vendorName = vendorTitle,
                            vendorLocation = GeoLocation(vLat, vLon),
                            dishName = dishName,
                            costBreakdown = costBreakdown,
                            originalUrl = "https://snappfood.ir/restaurant/menu/$vendorCode",
                            imageUrl = null,
                            isOpen = true
                        )
                    )
                }
                ProviderResult.Success(offers)
            }
        } catch (e: Exception) {
            ProviderResult.Error("SnappFood error: ${e.message}", cause = e)
        }
    }
}
