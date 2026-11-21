package com.teamwork.pricehunter.data.repository

import com.teamwork.pricehunter.core.intelligence.AccessoryAndOutlierFilter
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.provider.FoodProvider
import com.teamwork.pricehunter.core.provider.FoodVendorDishOffer
import com.teamwork.pricehunter.core.provider.ProviderResult
import com.teamwork.pricehunter.core.provider.ShoppingOffer
import com.teamwork.pricehunter.core.provider.ShoppingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

data class DetailedShoppingResults(
    val primaryOffers: List<ShoppingOffer> = emptyList(),
    val accessoryOffers: List<ShoppingOffer> = emptyList(),
    val outlierOffers: List<ShoppingOffer> = emptyList(),
    val medianPriceToman: Long = 0L
)

class SearchEngineRepository(
    private val shoppingProviders: MutableList<ShoppingProvider> = mutableListOf(),
    private val foodProviders: MutableList<FoodProvider> = mutableListOf(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun registerShoppingProvider(provider: ShoppingProvider) {
        synchronized(shoppingProviders) {
            shoppingProviders.removeAll { it.providerId == provider.providerId }
            shoppingProviders.add(provider)
        }
    }

    fun registerFoodProvider(provider: FoodProvider) {
        synchronized(foodProviders) {
            foodProviders.removeAll { it.providerId == provider.providerId }
            foodProviders.add(provider)
        }
    }

    fun getShoppingProviders(): List<ShoppingProvider> {
        return synchronized(shoppingProviders) { shoppingProviders.toList() }
    }

    fun getFoodProviders(): List<FoodProvider> {
        return synchronized(foodProviders) { foodProviders.toList() }
    }

    suspend fun searchShoppingParallel(query: String): List<ShoppingOffer> = withContext(dispatcher) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val allOffers = mutableListOf<ShoppingOffer>()
        val activeProviders = synchronized(shoppingProviders) {
            shoppingProviders.filter { it.isEnabled }
        }

        // SupervisorJob isolation: one provider error/timeout/403 never cancels others
        supervisorScope {
            val deferreds = activeProviders.map { provider ->
                async {
                    try {
                        when (val result = provider.searchProducts(cleanQuery)) {
                            is ProviderResult.Success -> result.data
                            is ProviderResult.Error -> emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            deferreds.awaitAll().forEach { offers ->
                allOffers.addAll(offers)
            }
        }

        // Apply Accessory and Statistical Outlier Filtering
        val filtered = AccessoryAndOutlierFilter.filter(allOffers, cleanQuery, 0.20)
        // Primary offers sorted strictly in ascending order of priceToman
        filtered.primaryResults.sortedBy { it.priceToman }
    }

    fun searchShoppingFlow(query: String): Flow<List<ShoppingOffer>> = channelFlow {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        val accumulatedOffers = mutableListOf<ShoppingOffer>()
        val activeProviders = synchronized(shoppingProviders) {
            shoppingProviders.filter { it.isEnabled }
        }

        if (activeProviders.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        activeProviders.forEach { provider ->
            launch {
                try {
                    val res = provider.searchProducts(cleanQuery)
                    if (res is ProviderResult.Success) {
                        val currentSorted = synchronized(accumulatedOffers) {
                            accumulatedOffers.addAll(res.data)
                            val filtered = AccessoryAndOutlierFilter.filter(accumulatedOffers, cleanQuery, 0.20)
                            filtered.primaryResults.sortedBy { it.priceToman }
                        }
                        send(currentSorted)
                    }
                } catch (_: Exception) {}
            }
        }
    }.flowOn(dispatcher)

    suspend fun searchShoppingDetailed(query: String): DetailedShoppingResults = withContext(dispatcher) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext DetailedShoppingResults()

        val allOffers = mutableListOf<ShoppingOffer>()
        val activeProviders = synchronized(shoppingProviders) {
            shoppingProviders.filter { it.isEnabled }
        }

        supervisorScope {
            val deferreds = activeProviders.map { provider ->
                async {
                    try {
                        when (val result = provider.searchProducts(cleanQuery)) {
                            is ProviderResult.Success -> result.data
                            is ProviderResult.Error -> emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            deferreds.awaitAll().forEach { offers ->
                allOffers.addAll(offers)
            }
        }

        val filtered = AccessoryAndOutlierFilter.filter(allOffers, cleanQuery, 0.20)
        DetailedShoppingResults(
            primaryOffers = filtered.primaryResults.sortedBy { it.priceToman },
            accessoryOffers = filtered.accessoryResults.sortedBy { it.priceToman },
            outlierOffers = filtered.suspiciousOutliers.sortedBy { it.priceToman },
            medianPriceToman = filtered.medianPriceToman
        )
    }

    fun searchShoppingDetailedFlow(query: String): Flow<DetailedShoppingResults> = channelFlow {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            send(DetailedShoppingResults())
            return@channelFlow
        }

        val accumulatedOffers = mutableListOf<ShoppingOffer>()
        val activeProviders = synchronized(shoppingProviders) {
            shoppingProviders.filter { it.isEnabled }
        }

        if (activeProviders.isEmpty()) {
            send(DetailedShoppingResults())
            return@channelFlow
        }

        activeProviders.forEach { provider ->
            launch {
                try {
                    val res = provider.searchProducts(cleanQuery)
                    if (res is ProviderResult.Success) {
                        val detailed = synchronized(accumulatedOffers) {
                            accumulatedOffers.addAll(res.data)
                            val filtered = AccessoryAndOutlierFilter.filter(accumulatedOffers, cleanQuery, 0.20)
                            DetailedShoppingResults(
                                primaryOffers = filtered.primaryResults.sortedBy { it.priceToman },
                                accessoryOffers = filtered.accessoryResults.sortedBy { it.priceToman },
                                outlierOffers = filtered.suspiciousOutliers.sortedBy { it.priceToman },
                                medianPriceToman = filtered.medianPriceToman
                            )
                        }
                        send(detailed)
                    }
                } catch (_: Exception) {}
            }
        }
    }.flowOn(dispatcher)

    suspend fun searchFoodParallel(
        query: String,
        userLocation: GeoLocation
    ): List<FoodVendorDishOffer> = withContext(dispatcher) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val allDishes = mutableListOf<FoodVendorDishOffer>()
        val activeProviders = synchronized(foodProviders) {
            foodProviders.filter { it.isEnabled }
        }

        // SupervisorJob isolation: one provider failure never interrupts remaining providers
        supervisorScope {
            val deferreds = activeProviders.map { provider ->
                async {
                    try {
                        when (val res = provider.searchFoodDishes(cleanQuery, userLocation)) {
                            is ProviderResult.Success -> res.data
                            is ProviderResult.Error -> emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            deferreds.awaitAll().forEach { dishes ->
                allDishes.addAll(dishes)
            }
        }

        // Sort by totalDeliveredCost strictly ascending
        allDishes.sortedBy { it.costBreakdown.totalCostToman }
    }

    fun searchFoodFlow(
        query: String,
        userLocation: GeoLocation
    ): Flow<List<FoodVendorDishOffer>> = channelFlow {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        val accumulatedDishes = mutableListOf<FoodVendorDishOffer>()
        val activeProviders = synchronized(foodProviders) {
            foodProviders.filter { it.isEnabled }
        }

        if (activeProviders.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        activeProviders.forEach { provider ->
            launch {
                try {
                    val res = provider.searchFoodDishes(cleanQuery, userLocation)
                    if (res is ProviderResult.Success) {
                        val currentSorted = synchronized(accumulatedDishes) {
                            accumulatedDishes.addAll(res.data)
                            accumulatedDishes.sortedBy { it.costBreakdown.totalCostToman }
                        }
                        send(currentSorted)
                    }
                } catch (_: Exception) {}
            }
        }
    }.flowOn(dispatcher)
}
