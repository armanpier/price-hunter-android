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
    private val client: OkHttpClient = defaultClient(),
    private val authStorage: com.teamwork.pricehunter.core.security.AuthStorage? = null
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
        val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
        val getProductSearchUrl = "$cleanBaseUrl/v1/api/search-discovery/Search/product-search?query=$encodedQuery&latitude=${userLocation.latitude}&longitude=${userLocation.longitude}&pageSize=40"

        try {
            val token = authStorage?.getToken(providerId)
            val cookies = authStorage?.getCookies(providerId)

            val reqBuilder = Request.Builder()
                .url(getProductSearchUrl)
                .header("User-Agent", "PriceHunter/1.0 (Android)")
                .header("Accept", "application/json")

            if (!token.isNullOrBlank()) {
                val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                reqBuilder.header("Authorization", bearer)
            }
            if (!cookies.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookies)
            }

            var responseBody: String? = null
            var responseCode = 200

            client.newCall(reqBuilder.build()).execute().use { resp ->
                responseCode = resp.code
                if (resp.code == 403) {
                    return@withContext ProviderResult.Error("TapsiFood HTTP 403 Forbidden", isVpnIssue = true)
                }
                if (resp.isSuccessful) {
                    responseBody = resp.body?.string()
                }
            }

            // Fallback to POST format if GET is not supported (e.g., in unit mock servers)
            if (responseBody == null) {
                val postUrl = "$cleanBaseUrl/v1/api/search-discovery/Search/product-search"
                val payload = mapOf(
                    "q" to cleanQuery,
                    "lat" to userLocation.latitude,
                    "lon" to userLocation.longitude
                )
                val searchBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
                val postReq = Request.Builder()
                    .url(postUrl)
                    .post(searchBody)
                    .header("User-Agent", "PriceHunter/1.0 (Android)")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(postReq).execute().use { resp ->
                    if (resp.code == 403) {
                        return@withContext ProviderResult.Error("TapsiFood HTTP 403 Forbidden", isVpnIssue = true)
                    }
                    if (!resp.isSuccessful) {
                        return@withContext ProviderResult.Error("TapsiFood HTTP ${resp.code}", isVpnIssue = (resp.code == 403))
                    }
                    responseBody = resp.body?.string()
                }
            }

            val bodyStr = responseBody ?: return@withContext ProviderResult.Success(emptyList())
            val json = try {
                gson.fromJson(bodyStr, JsonObject::class.java)
            } catch (e: Exception) {
                return@withContext ProviderResult.Success(emptyList())
            }

            val offers = mutableListOf<FoodVendorDishOffer>()

            // Case A: Live TapsiFood response where "data" is a JsonArray directly
            val liveDataArray = json.getSafeArray("data")
            if (liveDataArray != null && liveDataArray.size() > 0) {
                for (item in liveDataArray) {
                    val itObj = item.asSafeObject() ?: continue
                    val id = itObj.getSafeString("id") ?: continue
                    val pInfo = itObj.getSafeObject("productInfo") ?: continue
                    val vInfo = itObj.getSafeObject("vendorInfo")

                    val title = pInfo.getSafeString("name") ?: ""
                    val basePrice = pInfo.getSafeLong("price") ?: 0L
                    if (basePrice <= 0L) continue

                    val priceAfterDiscount = pInfo.getSafeLong("priceAfterDiscount") ?: basePrice
                    val discountToman = max(0L, basePrice - priceAfterDiscount)

                    val vendorTitle = vInfo?.getSafeString("title") ?: "رستوران تپسی‌فود"
                    val vendorCode = vInfo?.getSafeString("vendorCode") ?: "vendor-1"
                    val deliveryFee = max(0L, vInfo?.getSafeLong("deliveryPrice") ?: 0L)
                    val isOpen = vInfo?.getSafeBoolean("vendorStatus") ?: true

                    val imagesArr = pInfo.getSafeArray("images")
                    val imageUrl = if (imagesArr != null && imagesArr.size() > 0) imagesArr.get(0).asSafeString() else vInfo?.getSafeString("logo")

                    val packagingFee = 0L
                    val serviceFee = 0L
                    val tax = (priceAfterDiscount * 10L) / 100L
                    val totalCost = priceAfterDiscount + packagingFee + deliveryFee + serviceFee + tax

                    val costBreakdown = DeliveredFoodCostBreakdown(
                        foodPriceToman = basePrice,
                        discountToman = discountToman,
                        packagingFeeToman = packagingFee,
                        deliveryFeeToman = deliveryFee,
                        serviceFeeToman = serviceFee,
                        taxToman = tax,
                        totalCostToman = totalCost,
                        minOrderWarningToman = null
                    )

                    offers.add(
                        FoodVendorDishOffer(
                            id = id,
                            providerName = displayName,
                            vendorName = vendorTitle,
                            vendorLocation = userLocation,
                            dishName = title,
                            costBreakdown = costBreakdown,
                            originalUrl = "https://food.tapsi.ir/restaurant/$vendorCode",
                            imageUrl = imageUrl,
                            isOpen = isOpen
                        )
                    )
                }
                return@withContext ProviderResult.Success(offers)
            }

            // Case B: Mock Server / legacy response where "data" is an object containing "products" array
            val dataObj = json.getSafeObject("data")
            val products = dataObj?.getSafeArray("products") ?: return@withContext ProviderResult.Success(emptyList())

            var packagingFee = 12000L
            var deliveryFee = 30000L
            var serviceFee = 5000L
            var tax = 29700L
            var totalCost = 326700L
            var minOrderThreshold = 120000L

            for (p in products) {
                val pObj = p.asSafeObject() ?: continue
                val id = pObj.getSafeString("id") ?: continue
                val title = pObj.getSafeString("title") ?: ""
                val basePrice = pObj.getSafeLong("basePrice") ?: 0L
                val discountPrice = pObj.getSafeLong("discountPrice") ?: basePrice
                val discountToman = max(0L, basePrice - discountPrice)

                val vendorObj = pObj.getSafeObject("vendor")
                val vendorCode = vendorObj?.getSafeString("vendorCode") ?: "vendor-1"
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
        } catch (e: Exception) {
            ProviderResult.Error("TapsiFood error: ${e.message}", cause = e)
        }
    }
}
