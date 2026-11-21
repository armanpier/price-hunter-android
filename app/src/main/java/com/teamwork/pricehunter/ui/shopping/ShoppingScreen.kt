package com.teamwork.pricehunter.ui.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.teamwork.pricehunter.core.provider.ShoppingOffer
import com.teamwork.pricehunter.core.state.ProgressiveLoadingSnapshot
import com.teamwork.pricehunter.core.state.ProgressiveLoadingTracker
import com.teamwork.pricehunter.data.repository.DetailedShoppingResults
import com.teamwork.pricehunter.data.repository.SearchEngineRepository
import com.teamwork.pricehunter.ui.components.ProgressiveLoadingBar
import com.teamwork.pricehunter.ui.components.VpnWarningBanner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ShoppingScreen(
    repository: SearchEngineRepository,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    isVpnActive: Boolean = false
) {
    var query by remember { mutableStateOf(initialQuery) }
    var detailedResults by remember { mutableStateOf(DetailedShoppingResults()) }
    var isSearching by remember { mutableStateOf(false) }
    var isAccessoriesExpanded by remember { mutableStateOf(false) }
    var isOutliersExpanded by remember { mutableStateOf(false) }
    var vpnBannerDismissed by remember { mutableStateOf(false) }

    var shoppingTracker by remember {
        mutableStateOf(ProgressiveLoadingTracker(listOf("digikala", "torob", "snappshop")))
    }
    var loadingSnapshot by remember { mutableStateOf(shoppingTracker.getSnapshot()) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val onSearchExecute = {
        val cleanQuery = query.trim()
        if (cleanQuery.isNotBlank()) {
            isSearching = true
            isAccessoriesExpanded = false
            val newTracker = ProgressiveLoadingTracker(listOf("digikala", "torob", "snappshop"))
            listOf("digikala", "torob", "snappshop").forEach { newTracker.startLoading(it) }
            shoppingTracker = newTracker
            loadingSnapshot = newTracker.getSnapshot()

            coroutineScope.launch {
                repository.searchShoppingDetailedFlow(cleanQuery).collectLatest { results ->
                    detailedResults = results
                    // Mark providers as success/complete as results arrive
                    listOf("digikala", "torob", "snappshop").forEach { id ->
                        val isMatched = results.primaryOffers.any { offer ->
                            when (id) {
                                "digikala" -> offer.providerName.contains("دیجی") || offer.providerName.contains("digikala", ignoreCase = true)
                                "torob" -> offer.providerName.contains("ترب") || offer.providerName.contains("torob", ignoreCase = true)
                                "snappshop" -> offer.providerName.contains("اسنپ") || offer.providerName.contains("snapp", ignoreCase = true)
                                else -> offer.providerName.contains(id, ignoreCase = true)
                            }
                        }
                        if (isMatched) {
                            shoppingTracker.markSuccess(id)
                        }
                    }
                    loadingSnapshot = shoppingTracker.getSnapshot()
                    isSearching = false
                }
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            focusManager.clearFocus()
            onSearchExecute()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("shopping_screen")
    ) {
        // Search Input Bar with Clear Button
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("shopping_search_bar"),
            placeholder = { Text("جستجوی کالا (مثلاً گوشی سامسونگ، لپ‌تاپ...)") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "جستجو",
                    modifier = Modifier.clickable {
                        focusManager.clearFocus()
                        onSearchExecute()
                    }
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            detailedResults = DetailedShoppingResults()
                            val resetTracker = ProgressiveLoadingTracker(listOf("digikala", "torob", "snappshop"))
                            shoppingTracker = resetTracker
                            loadingSnapshot = resetTracker.getSnapshot()
                        },
                        modifier = Modifier.testTag("shopping_search_clear_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "پاک کردن")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                onSearchExecute()
            })
        )

        // VPN Advisory Banner
        if (isVpnActive && !vpnBannerDismissed) {
            VpnWarningBanner(
                isVpnActive = true,
                onDismiss = { vpnBannerDismissed = true }
            )
        }

        // Multi-Stage Progressive Loading Bar & Provider Status Chips
        if (isSearching || loadingSnapshot.statuses.any { it.value != com.teamwork.pricehunter.core.state.ServiceStatus.IDLE }) {
            ProgressiveLoadingBar(
                snapshot = loadingSnapshot,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Main Offers Content Area
        // Primary merchandise offers strictly sorted by price
        val primaryOffers = remember(detailedResults) {
            detailedResults.primaryOffers.sortedBy { it.priceToman }
        }
        val outlierOffers = remember(detailedResults) {
            detailedResults.outlierOffers.sortedBy { it.priceToman }
        }
        val accessoryOffers = remember(detailedResults) {
            detailedResults.accessoryOffers.sortedBy { it.priceToman }
        }

        val hasAnyResults = primaryOffers.isNotEmpty() || outlierOffers.isNotEmpty() || accessoryOffers.isNotEmpty()

        if (!hasAnyResults && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "کالای مورد نظر خود را جستجو کنید" else "هیچ کالایی برای «$query» یافت نشد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("shopping_primary_offers_list"),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Primary Merchandise Offers (Ranked strictly by priceToman ascending)
                items(primaryOffers, key = { "${it.providerName}_${it.id}" }) { offer ->
                    ShoppingOfferCard(
                        offer = offer,
                        isOutlier = false
                    )
                }

                // Collapsible Section for Filtered Outliers (< 20% of median)
                if (outlierOffers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { isOutliersExpanded = !isOutliersExpanded }
                                .testTag("shopping_outliers_header"),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "کالاهای با قیمت نامتعارف یا مشکوک (${outlierOffers.size} مورد فیلتر شده)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Icon(
                                    imageVector = if (isOutliersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isOutliersExpanded) "بستن" else "باز کردن",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (isOutliersExpanded) {
                        items(outlierOffers, key = { "outlier_${it.providerName}_${it.id}" }) { outlier ->
                            ShoppingOfferCard(
                                offer = outlier,
                                isOutlier = true,
                                modifier = Modifier.testTag("shopping_outlier_card")
                            )
                        }
                    }
                }

                // Collapsible Secondary Section for Filtered Accessories
                if (accessoryOffers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { isAccessoriesExpanded = !isAccessoriesExpanded }
                                .testTag("shopping_accessories_header"),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "لوازم جانبی مرتبط (${accessoryOffers.size} مورد فیلتر شده)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (isAccessoriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isAccessoriesExpanded) "بستن" else "باز کردن",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isAccessoriesExpanded) {
                        items(accessoryOffers, key = { "acc_${it.providerName}_${it.id}" }) { accessory ->
                            ShoppingOfferCard(
                                offer = accessory,
                                isOutlier = false,
                                modifier = Modifier.testTag("shopping_accessory_card")
                            )
                        }
                    }
                }
            }
        }
    }
}
