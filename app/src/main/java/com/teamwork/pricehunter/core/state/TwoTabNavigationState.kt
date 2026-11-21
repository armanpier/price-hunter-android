package com.teamwork.pricehunter.core.state

import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.provider.FoodVendorDishOffer
import com.teamwork.pricehunter.core.provider.ShoppingOffer

enum class NavigationTab {
    SHOPPING, FOOD
}

data class ShoppingTabState(
    val query: String = "",
    val offers: List<ShoppingOffer> = emptyList(),
    val isSearching: Boolean = false
)

data class FoodTabState(
    val query: String = "",
    val userLocation: GeoLocation? = null,
    val dishes: List<FoodVendorDishOffer> = emptyList(),
    val isSearching: Boolean = false
)

class TwoTabNavigationStateMachine(
    initialTab: NavigationTab = NavigationTab.SHOPPING
) {
    var activeTab: NavigationTab = initialTab
        private set

    var shoppingState: ShoppingTabState = ShoppingTabState()
        private set

    var foodState: FoodTabState = FoodTabState()
        private set

    fun selectTab(tab: NavigationTab) {
        activeTab = tab
    }

    fun updateShoppingQuery(query: String) {
        shoppingState = shoppingState.copy(query = query)
    }

    fun updateShoppingResults(offers: List<ShoppingOffer>) {
        shoppingState = shoppingState.copy(offers = offers, isSearching = false)
    }

    fun updateFoodQuery(query: String) {
        foodState = foodState.copy(query = query)
    }

    fun updateFoodLocation(location: GeoLocation) {
        foodState = foodState.copy(userLocation = location)
    }

    fun updateFoodResults(dishes: List<FoodVendorDishOffer>) {
        foodState = foodState.copy(dishes = dishes, isSearching = false)
    }
}
