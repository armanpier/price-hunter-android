package com.teamwork.pricehunter.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.teamwork.pricehunter.core.intelligence.GeoLocation
import com.teamwork.pricehunter.core.provider.FoodVendorDishOffer
import com.teamwork.pricehunter.core.state.LocationPickerState
import com.teamwork.pricehunter.core.state.ProgressiveLoadingTracker
import com.teamwork.pricehunter.data.repository.SearchEngineRepository
import com.teamwork.pricehunter.ui.components.ProgressiveLoadingBar
import com.teamwork.pricehunter.ui.components.VpnWarningBanner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FoodScreen(
    repository: SearchEngineRepository,
    modifier: Modifier = Modifier,
    initialLocation: GeoLocation = LocationPickerState.DEFAULT_TEHRAN_LOCATION,
    isVpnActive: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(initialLocation) }
    var offers by remember { mutableStateOf<List<FoodVendorDishOffer>>(emptyList()) }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("food_screen")
    ) {
        // Location Selector Header (Tapping opens OSMDroid Map Dialog)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { showLocationPicker = true }
                .testTag("food_location_header"),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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

        // Multi-Stage Progressive Loading Bar & Status Badges
        if (isSearching || loadingSnapshot.statuses.any { it.value != com.teamwork.pricehunter.core.state.ServiceStatus.IDLE }) {
            ProgressiveLoadingBar(
                snapshot = loadingSnapshot,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Food Dishes List or Empty State (ranked strictly by total delivered cost)
        if (offers.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "غذای مورد نظر خود را جستجو کنید" else "هیچ پیشنهادی برای «$query» در محدوده انتخابی یافت نشد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("food_offers_list"),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(offers, key = { "${it.providerName}_${it.id}" }) { offer ->
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
