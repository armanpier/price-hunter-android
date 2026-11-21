package com.teamwork.pricehunter.ui.food

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.unit.sp
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.provider.FoodVendorDishOffer
import com.teamwork.pricehunter.core.state.LocationPickerState
import com.teamwork.pricehunter.core.state.ProgressiveLoadingTracker
import com.teamwork.pricehunter.data.repository.SearchEngineRepository
import com.teamwork.pricehunter.ui.components.OfferCardSkeleton
import com.teamwork.pricehunter.ui.components.ProgressiveLoadingBar
import com.teamwork.pricehunter.ui.components.VpnWarningBanner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

enum class FoodSortMode(val label: String) {
    CHEAPEST_FOOD("ارزان‌ترین غذا (خالص)"),
    CHEAPEST_DELIVERED("ارزان‌ترین با پیک"),
    DISCOUNTED("فقط تخفیف‌دارها")
}

@Composable
fun FoodScreen(
    repository: SearchEngineRepository,
    modifier: Modifier = Modifier,
    initialLocation: GeoLocation = LocationPickerState.DEFAULT_TEHRAN_LOCATION,
    initialQuery: String = "",
    isVpnActive: Boolean = false
) {
    var query by remember { mutableStateOf(initialQuery) }
    var location by remember { mutableStateOf(initialLocation) }
    var offers by remember { mutableStateOf<List<FoodVendorDishOffer>>(emptyList()) }
    var selectedSortMode by remember { mutableStateOf(FoodSortMode.CHEAPEST_FOOD) }
    var isSearching by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var vpnBannerDismissed by remember { mutableStateOf(false) }

    var foodTracker by remember {
        mutableStateOf(ProgressiveLoadingTracker(listOf("snappfood", "tapsifood")))
    }
    var loadingSnapshot by remember { mutableStateOf(foodTracker.getSnapshot()) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val onSearchExecute = {
        val cleanQuery = query.trim()
        if (cleanQuery.isNotBlank()) {
            isSearching = true
            val newTracker = ProgressiveLoadingTracker(listOf("snappfood", "tapsifood"))
            listOf("snappfood", "tapsifood").forEach { newTracker.startLoading(it) }
            foodTracker = newTracker
            loadingSnapshot = newTracker.getSnapshot()

            coroutineScope.launch {
                repository.searchFoodFlow(cleanQuery, location).collectLatest { updatedOffers ->
                    offers = updatedOffers
                    listOf("snappfood", "tapsifood").forEach { id ->
                        val isMatched = updatedOffers.any { offer ->
                            when (id) {
                                "snappfood" -> offer.providerName.contains("اسنپ") || offer.providerName.contains("snapp", ignoreCase = true)
                                "tapsifood" -> offer.providerName.contains("تپسی") || offer.providerName.contains("tapsi", ignoreCase = true)
                                else -> offer.providerName.contains(id, ignoreCase = true)
                            }
                        }
                        if (isMatched) {
                            foodTracker.markSuccess(id)
                        }
                    }
                    loadingSnapshot = foodTracker.getSnapshot()
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

    val displayOffers = remember(offers, selectedSortMode) {
        when (selectedSortMode) {
            FoodSortMode.CHEAPEST_FOOD -> offers.sortedBy {
                (it.costBreakdown.foodPriceToman - it.costBreakdown.discountToman).coerceAtLeast(0L)
            }
            FoodSortMode.CHEAPEST_DELIVERED -> offers.sortedBy {
                it.costBreakdown.totalCostToman
            }
            FoodSortMode.DISCOUNTED -> offers
                .filter { it.costBreakdown.discountToman > 0 }
                .sortedBy {
                    (it.costBreakdown.foodPriceToman - it.costBreakdown.discountToman).coerceAtLeast(0L)
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("food_screen")
    ) {
        // Location Selector Header (Tapping opens OSMDroid Map Dialog)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { showLocationPicker = true }
                .testTag("food_location_header"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "موقعیت تحویل سفارش:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "تهران (${String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "تغییر موقعیت",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("food_search_bar"),
            placeholder = { Text("جستجوی غذا یا رستوران (مثلاً کباب، پیتزا...)") },
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
                            offers = emptyList()
                            val resetTracker = ProgressiveLoadingTracker(listOf("snappfood", "tapsifood"))
                            foodTracker = resetTracker
                            loadingSnapshot = resetTracker.getSnapshot()
                        },
                        modifier = Modifier.testTag("food_search_clear_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "پاک کردن")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                onSearchExecute()
            })
        )

        // Sort & Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(FoodSortMode.values()) { mode ->
                FilterChip(
                    selected = selectedSortMode == mode,
                    onClick = { selectedSortMode = mode },
                    label = { Text(mode.label, fontSize = 12.sp, fontWeight = if (selectedSortMode == mode) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // VPN Advisory Banner
        if (isVpnActive && !vpnBannerDismissed) {
            VpnWarningBanner(
                isVpnActive = true,
                onDismiss = { vpnBannerDismissed = true }
            )
        }

        // Multi-Stage Progressive Loading Bar & Status Badges
        if (isSearching || loadingSnapshot.statuses.any { it.value != com.teamwork.pricehunter.core.state.ServiceStatus.IDLE }) {
            ProgressiveLoadingBar(
                snapshot = loadingSnapshot,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Food Dishes List, Skeleton Loader, or Empty State
        if (isSearching && displayOffers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                repeat(4) {
                    OfferCardSkeleton()
                }
            }
        } else if (displayOffers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (query.isBlank()) "غذای مورد نظر خود را جستجو کنید" else "هیچ پیشنهادی برای «$query» در محدوده انتخابی یافت نشد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "ارزان‌ترین غذاها و رستوران‌ها را با احتساب هزینه پیک و مالیات بیابید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (query.isBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "پیشنهادهای محبوب:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val suggestions = listOf("پیتزا", "ساندویچ", "کباب کوبیده", "برگر", "زرشک پلو", "سوپ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        suggestions.forEach { item ->
                            SuggestionChip(
                                onClick = {
                                    query = item
                                    focusManager.clearFocus()
                                    onSearchExecute()
                                },
                                label = { Text(item, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("food_offers_list"),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(displayOffers, key = { "${it.providerName}_${it.id}" }) { offer ->
                    FoodOfferCard(offer = offer)
                }
            }
        }

        // OSMDroid Interactive Map Dialog
        if (showLocationPicker) {
            LocationPickerDialog(
                initialLocation = location,
                onLocationConfirmed = { newLocation ->
                    location = newLocation
                    if (query.isNotBlank()) {
                        onSearchExecute()
                    }
                },
                onDismiss = { showLocationPicker = false }
            )
        }
    }
}
