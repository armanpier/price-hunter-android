package com.teamwork.pricehunter.ui

import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.provider.ShoppingOffer
import com.teamwork.pricehunter.core.state.NavigationTab
import com.teamwork.pricehunter.core.state.TwoTabNavigationStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TwoTabNavigationStateMachineTest {

    @Test
    fun testTabSwitching() {
        val stateMachine = TwoTabNavigationStateMachine(NavigationTab.SHOPPING)
        assertEquals(NavigationTab.SHOPPING, stateMachine.activeTab)

        stateMachine.selectTab(NavigationTab.FOOD)
        assertEquals(NavigationTab.FOOD, stateMachine.activeTab)

        stateMachine.selectTab(NavigationTab.SHOPPING)
        assertEquals(NavigationTab.SHOPPING, stateMachine.activeTab)
    }

    @Test
    fun testShoppingStateUpdates() {
        val stateMachine = TwoTabNavigationStateMachine()
        stateMachine.updateShoppingQuery("گوشی موبایل")
        assertEquals("گوشی موبایل", stateMachine.shoppingState.query)

        val offer = ShoppingOffer(
            id = "dkp-1",
            providerName = "دیجی‌کالا",
            title = "سامسونگ S24",
            rawPrice = 500000000L,
            priceToman = 50000000L,
            originalUrl = "https://digikala.com/1"
        )
        stateMachine.updateShoppingResults(listOf(offer))
        assertEquals(1, stateMachine.shoppingState.offers.size)
        assertFalse(stateMachine.shoppingState.isSearching)
    }

    @Test
    fun testFoodStateUpdates() {
        val stateMachine = TwoTabNavigationStateMachine()
        stateMachine.updateFoodQuery("چلوکباب")
        assertEquals("چلوکباب", stateMachine.foodState.query)

        val location = GeoLocation(35.7000, 51.4000)
        stateMachine.updateFoodLocation(location)
        assertEquals(location, stateMachine.foodState.userLocation)
    }
}
