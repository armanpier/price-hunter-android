package com.teamwork.pricehunter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.teamwork.pricehunter.core.network.DebugCaptureInterceptor
import com.teamwork.pricehunter.core.network.DebugNetworkCapturePool
import com.teamwork.pricehunter.core.provider.DigikalaProvider
import com.teamwork.pricehunter.core.provider.OkalaProvider
import com.teamwork.pricehunter.core.provider.SnappFoodProvider
import com.teamwork.pricehunter.core.provider.SnappShopProvider
import com.teamwork.pricehunter.core.provider.TapsiFoodProvider
import com.teamwork.pricehunter.core.provider.TorobProvider
import com.teamwork.pricehunter.core.security.AuthStorage
import com.teamwork.pricehunter.core.security.NetworkGuard
import com.teamwork.pricehunter.core.security.NetworkStatus
import com.teamwork.pricehunter.core.security.SecureAuthStorage
import com.teamwork.pricehunter.core.state.NavigationTab
import com.teamwork.pricehunter.core.state.TwoTabNavigationStateMachine
import com.teamwork.pricehunter.data.repository.SearchEngineRepository
import com.teamwork.pricehunter.ui.debug.DebugNetworkCaptureView
import com.teamwork.pricehunter.ui.debug.ManualAuthHeaderDialog
import com.teamwork.pricehunter.ui.food.FoodScreen
import com.teamwork.pricehunter.ui.shopping.ShoppingScreen
import com.teamwork.pricehunter.ui.theme.PriceHunterTheme
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val capturePool = DebugNetworkCapturePool(maxCapacity = 100)
    private lateinit var authStorage: AuthStorage
    private lateinit var repository: SearchEngineRepository
    private lateinit var networkGuard: NetworkGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize security and network services
        authStorage = SecureAuthStorage.create(applicationContext)
        networkGuard = NetworkGuard(applicationContext)

        val debugInterceptor = DebugCaptureInterceptor(capturePool = capturePool, isDebugEnabled = true)
        val debugOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(debugInterceptor)
            .build()

        // Initialize and register all providers
        val digikala = DigikalaProvider(client = debugOkHttpClient)
        val torob = TorobProvider(client = debugOkHttpClient)
        val snappShop = SnappShopProvider(client = debugOkHttpClient)
        val snappFood = SnappFoodProvider(client = debugOkHttpClient)
        val tapsiFood = TapsiFoodProvider(client = debugOkHttpClient)
        val okala = OkalaProvider(client = debugOkHttpClient)

        repository = SearchEngineRepository(
            shoppingProviders = mutableListOf(digikala, torob, snappShop),
            foodProviders = mutableListOf(snappFood, tapsiFood)
        )
        repository.registerShoppingProvider(okala)

        setContent {
            PriceHunterTheme {
                MainAppScreen(
                    repository = repository,
                    authStorage = authStorage,
                    capturePool = capturePool,
                    networkGuard = networkGuard
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    repository: SearchEngineRepository,
    authStorage: AuthStorage,
    capturePool: DebugNetworkCapturePool,
    networkGuard: NetworkGuard? = null
) {
    val navStateMachine = remember { TwoTabNavigationStateMachine(NavigationTab.SHOPPING) }
    var activeTab by remember { mutableStateOf(navStateMachine.activeTab) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showDebugCapture by remember { mutableStateOf(false) }

    val isVpnActive = remember {
        val status = networkGuard?.checkNetworkStatus()
        status is NetworkStatus.ConnectedWithVpn
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "شکارچی قیمت",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    // Manual Auth Header Fallback Dialog (Debug)
                    IconButton(
                        onClick = { showAuthDialog = true },
                        modifier = Modifier.testTag("action_auth_debug")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "احراز هویت دستی"
                        )
                    }

                    // Debug Network Capture Tool
                    IconButton(
                        onClick = { showDebugCapture = true },
                        modifier = Modifier.testTag("action_network_debug")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "گزارش شبکه"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = activeTab == NavigationTab.SHOPPING,
                    onClick = {
                        navStateMachine.selectTab(NavigationTab.SHOPPING)
                        activeTab = NavigationTab.SHOPPING
                    },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "خرید کالا") },
                    label = { Text("خرید کالا") },
                    modifier = Modifier.testTag("tab_shopping")
                )
                NavigationBarItem(
                    selected = activeTab == NavigationTab.FOOD,
                    onClick = {
                        navStateMachine.selectTab(NavigationTab.FOOD)
                        activeTab = NavigationTab.FOOD
                    },
                    icon = { Icon(Icons.Default.Place, contentDescription = "سفارش غذا") },
                    label = { Text("سفارش غذا") },
                    modifier = Modifier.testTag("tab_food")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeTab) {
                NavigationTab.SHOPPING -> {
                    ShoppingScreen(
                        repository = repository,
                        isVpnActive = isVpnActive
                    )
                }
                NavigationTab.FOOD -> {
                    FoodScreen(
                        repository = repository,
                        isVpnActive = isVpnActive
                    )
                }
            }
        }

        // Debug Manual Auth Header Dialog
        if (showAuthDialog) {
            ManualAuthHeaderDialog(
                authStorage = authStorage,
                onDismiss = { showAuthDialog = false }
            )
        }

        // In-App Debug Network Capture Viewer
        if (showDebugCapture) {
            DebugNetworkCaptureView(
                capturePool = capturePool,
                onDismiss = { showDebugCapture = false }
            )
        }
    }
}
