package com.teamwork.pricehunter.core.provider

import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.intelligence.PriceableItem

enum class ProviderType {
    SHOPPING, FOOD, GROCERY
}

sealed class ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>()
    data class Error(val message: String, val cause: Throwable? = null, val isVpnIssue: Boolean = false) : ProviderResult<Nothing>()
}

data class ShoppingOffer(
    val id: String,
    val providerName: String,
    override val title: String,
    val rawPrice: Long,
    override val priceToman: Long,
    val originalUrl: String,
    val imageUrl: String? = null,
    val sellerName: String? = null,
    val ratingOutOf5: Float? = null,
    val isAvailable: Boolean = true
) : PriceableItem

data class DeliveredFoodCostBreakdown(
    val foodPriceToman: Long,
    val discountToman: Long,
    val packagingFeeToman: Long,
    val deliveryFeeToman: Long,
    val serviceFeeToman: Long,
    val taxToman: Long,
    val totalCostToman: Long,
    val minOrderWarningToman: Long? = null
)

data class FoodVendorDishOffer(
    val id: String,
    val providerName: String,
    val vendorName: String,
    val vendorLocation: GeoLocation,
    val dishName: String,
    val costBreakdown: DeliveredFoodCostBreakdown,
    val originalUrl: String,
    val imageUrl: String? = null,
    val isOpen: Boolean = true
)

interface BaseProvider {
    val providerId: String
    val displayName: String
    val providerType: ProviderType
    var isEnabled: Boolean
}

interface ShoppingProvider : BaseProvider {
    suspend fun searchProducts(query: String): ProviderResult<List<ShoppingOffer>>
}

interface FoodProvider : BaseProvider {
    suspend fun searchFoodDishes(
        query: String,
        userLocation: GeoLocation
    ): ProviderResult<List<FoodVendorDishOffer>>
}
