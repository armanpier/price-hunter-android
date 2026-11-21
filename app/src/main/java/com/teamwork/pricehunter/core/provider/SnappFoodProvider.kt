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
    private val client: OkHttpClient = defaultClient(),
    private val authStorage: com.teamwork.pricehunter.core.security.AuthStorage? = null
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
        val dishUrl = "$cleanBaseUrl/search/api/v5/search?lat=${userLocation.latitude}&long=${userLocation.longitude}&query=$encodedQuery&page=0&size=50"
        val stateUrl = "$cleanBaseUrl/mobile/v2/restaurant/details/state"

        try {
            val dishReqBuilder = Request.Builder()
                .url(dishUrl)
                .header("User-Agent", "PriceHunter/1.0 (Android)")
                .header("Accept", "application/json")

            val token = authStorage?.getToken(providerId)
            val cookies = authStorage?.getCookies(providerId)
            if (!token.isNullOrBlank()) {
                val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                dishReqBuilder.header("Authorization", bearer)
            }
            if (!cookies.isNullOrBlank()) {
                dishReqBuilder.header("Cookie", cookies)
            }

            val dishRequest = dishReqBuilder.build()

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

                // Fetch dynamic state for packaging, tax, delivery fee as fallback
                var fallbackDeliveryFee = 30000L
                var fallbackPackagingFee = 15000L
                var fallbackTaxPercent = 10L
                var fallbackMinOrder = 150000L

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
                                fallbackDeliveryFee = max(0L, baseFee - delDiscount)
                                fallbackPackagingFee = stateData.getSafeLong("packaging_fee") ?: 15000L
                                fallbackTaxPercent = stateData.getSafeLong("tax_percent") ?: 10L
                                fallbackMinOrder = stateData.getSafeLong("minOrder") ?: 150000L
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
                    val vLat = vendorObj?.getSafeDouble("latitude")
                        ?: vendorObj?.getSafeDouble("lat") ?: userLocation.latitude
                    val vLon = vendorObj?.getSafeDouble("longitude")
                        ?: vendorObj?.getSafeDouble("lon") ?: userLocation.longitude

                    // Use vendor delivery fee if explicitly present; otherwise use state fallback
                    val deliveryFee = vendorObj?.getSafeLong("deliveryFeeAfterDiscount")
                        ?: vendorObj?.getSafeLong("delivery_fee")
                        ?: fallbackDeliveryFee
                    val packagingFee = vendorObj?.getSafeLong("packaging_fee") ?: fallbackPackagingFee
                    val minOrder = vendorObj?.getSafeLong("minOrder") ?: fallbackMinOrder
                    val taxPercent = fallbackTaxPercent

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

                    val imageUrl = itObj.getSafeString("featured")
                        ?: itObj.getSafeString("image")
                        ?: vendorObj?.getSafeString("logo")

                    val isOpen = vendorObj?.getSafeBoolean("is_open") ?: true

                    offers.add(
                        FoodVendorDishOffer(
                            id = id,
                            providerName = displayName,
                            vendorName = vendorTitle,
                            vendorLocation = GeoLocation(vLat, vLon),
                            dishName = dishName,
                            costBreakdown = costBreakdown,
                            originalUrl = "https://snappfood.ir/restaurant/menu/$vendorCode",
                            imageUrl = imageUrl,
                            isOpen = isOpen
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
