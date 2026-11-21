package com.teamwork.pricehunter.core.provider

import kotlin.math.max
import kotlin.math.min

object FoodCostCalculator {
    fun calculateDeliveredCost(
        foodPriceToman: Long,
        discountToman: Long = 0L,
        packagingFeeToman: Long = 0L,
        deliveryFeeToman: Long = 0L,
        deliveryDiscountToman: Long = 0L,
        serviceFeeToman: Long = 0L,
        taxPercent: Long = 0L,
        minOrderThresholdToman: Long = 0L
    ): DeliveredFoodCostBreakdown {
        val safeFoodPrice = max(0L, foodPriceToman)
        // Ensure discount cannot be negative or exceed food price
        val safeDiscount = min(safeFoodPrice, max(0L, discountToman))
        val subtotalAfterDiscount = safeFoodPrice - safeDiscount
        val safePackaging = max(0L, packagingFeeToman)
        val netDeliveryFee = max(0L, deliveryFeeToman - max(0L, deliveryDiscountToman))
        val safeService = max(0L, serviceFeeToman)
        val taxToman = ((subtotalAfterDiscount + safePackaging) * max(0L, taxPercent)) / 100L
        val totalCost = subtotalAfterDiscount + safePackaging + netDeliveryFee + safeService + taxToman

        val minOrderWarning = if (subtotalAfterDiscount < minOrderThresholdToman) {
            minOrderThresholdToman - subtotalAfterDiscount
        } else null

        return DeliveredFoodCostBreakdown(
            foodPriceToman = safeFoodPrice,
            discountToman = safeDiscount,
            packagingFeeToman = safePackaging,
            deliveryFeeToman = netDeliveryFee,
            serviceFeeToman = safeService,
            taxToman = taxToman,
            totalCostToman = totalCost,
            minOrderWarningToman = minOrderWarning
        )
    }
}
