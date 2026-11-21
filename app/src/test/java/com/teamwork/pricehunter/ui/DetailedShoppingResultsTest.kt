package com.teamwork.pricehunter.ui

import com.teamwork.pricehunter.core.provider.ProviderResult
import com.teamwork.pricehunter.core.provider.ShoppingOffer
import com.teamwork.pricehunter.core.provider.ShoppingProvider
import com.teamwork.pricehunter.core.provider.ProviderType
import com.teamwork.pricehunter.data.repository.SearchEngineRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailedShoppingResultsTest {

    private class FakeShoppingProvider(
        override val providerId: String,
        override val displayName: String,
        private val offers: List<ShoppingOffer>
    ) : ShoppingProvider {
        override val providerType = ProviderType.SHOPPING
        override var isEnabled: Boolean = true
        override suspend fun searchProducts(query: String): ProviderResult<List<ShoppingOffer>> {
            return ProviderResult.Success(offers)
        }
    }

    @Test
    fun testDetailedShoppingSeparatesAccessoriesAndOutliers() = runBlocking {
        val phone1 = ShoppingOffer(
            id = "p1", providerName = "دیجی‌کالا", title = "گوشی موبایل آیفون 13",
            rawPrice = 600000000L, priceToman = 60000000L, originalUrl = "url1"
        )
        val phone2 = ShoppingOffer(
            id = "p2", providerName = "ترب", title = "گوشی آیفون 13 نرمال",
            rawPrice = 58000000L, priceToman = 58000000L, originalUrl = "url2"
        )
        // Accessory item (قاب گوشی)
        val accessory = ShoppingOffer(
            id = "a1", providerName = "دیجی‌کالا", title = "قاب سیلیکونی آیفون 13",
            rawPrice = 2500000L, priceToman = 250000L, originalUrl = "url3"
        )
        // Outlier item (<20% of ~59M median, e.g. 5,000,000 Toman)
        val outlier = ShoppingOffer(
            id = "o1", providerName = "اسنپ‌شاپ", title = "گوشی آیفون 13 فیک",
            rawPrice = 5000000L, priceToman = 5000000L, originalUrl = "url4"
        )

        val provider = FakeShoppingProvider(
            "fake", "فروشگاه تست",
            listOf(phone1, phone2, accessory, outlier)
        )
        val repository = SearchEngineRepository(shoppingProviders = mutableListOf(provider))

        val detailed = repository.searchShoppingDetailed("آیفون 13")

        // Primary results should only contain phone1 and phone2, sorted by price ascending
        assertEquals(2, detailed.primaryOffers.size)
        assertEquals(58000000L, detailed.primaryOffers[0].priceToman)
        assertEquals(60000000L, detailed.primaryOffers[1].priceToman)

        // Accessory should be filtered into accessoryOffers
        assertEquals(1, detailed.accessoryOffers.size)
        assertEquals("قاب سیلیکونی آیفون 13", detailed.accessoryOffers[0].title)

        // Outlier (< 20% of median) should be flagged in outlierOffers
        assertEquals(1, detailed.outlierOffers.size)
        assertEquals(5000000L, detailed.outlierOffers[0].priceToman)

        assertTrue("Median price should be ~59,000,000", detailed.medianPriceToman > 50000000L)
    }
}
