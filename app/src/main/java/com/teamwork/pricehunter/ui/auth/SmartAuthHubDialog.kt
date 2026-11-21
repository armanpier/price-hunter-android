package com.teamwork.pricehunter.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.teamwork.pricehunter.core.security.AuthStorage
import com.teamwork.pricehunter.ui.debug.ManualAuthHeaderDialog

data class ProviderAuthMeta(
    val id: String,
    val displayName: String,
    val loginUrl: String,
    val brandColor: Color
)

val SUPPORTED_AUTH_PROVIDERS = listOf(
    ProviderAuthMeta(
        id = "snappfood",
        displayName = "اسنپ‌فود (SnappFood)",
        loginUrl = "https://snappfood.ir",
        brandColor = Color(0xFFE91E63)
    ),
    ProviderAuthMeta(
        id = "tapsifood",
        displayName = "تپسی‌فود (TapsiFood)",
        loginUrl = "https://tapsi.food",
        brandColor = Color(0xFFFF6F00)
    ),
    ProviderAuthMeta(
        id = "digikala",
        displayName = "دیجی‌کالا (Digikala)",
        loginUrl = "https://www.digikala.com/users/login-register/",
        brandColor = Color(0xFFEF394E)
    ),
    ProviderAuthMeta(
        id = "snappshop",
        displayName = "اسنپ‌شاپ (SnappShop)",
        loginUrl = "https://snapp.shop",
        brandColor = Color(0xFF0056D2)
    )
)

@Composable
fun SmartAuthHubDialog(
    authStorage: AuthStorage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeBrowserProvider by remember { mutableStateOf<ProviderAuthMeta?>(null) }
    var showManualDeveloperDialog by remember { mutableStateOf(false) }

    // Re-check auth status when returning from browser login
    var refreshTrigger by remember { mutableStateOf(0) }

    if (activeBrowserProvider != null) {
        InAppBrowserLoginDialog(
            meta = activeBrowserProvider!!,
            authStorage = authStorage,
            onClose = {
                activeBrowserProvider = null
                refreshTrigger++
            }
        )
    }

    if (showManualDeveloperDialog) {
        ManualAuthHeaderDialog(
            authStorage = authStorage,
            onDismiss = {
                showManualDeveloperDialog = false
                refreshTrigger++
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_auth_hub_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ورود هوشمند به حساب کاربری",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "با ورود به حساب هر سرویس، تخفیف‌های اختصاصی، سبد خرید و هزینه دقیق پیک به طور خودکار محاسبه می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                SUPPORTED_AUTH_PROVIDERS.forEach { meta ->
                    // Determine login status
                    val hasToken = !authStorage.getToken(meta.id).isNullOrBlank()
                    val hasCookies = !authStorage.getCookies(meta.id).isNullOrBlank()
                    val isLoggedIn = hasToken || hasCookies

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = meta.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLoggedIn) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                                ) {
                                    Text(
                                        text = if (isLoggedIn) "متصل شد ✓" else "مهمان (وارد نشده)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLoggedIn) Color(0xFF2E7D32) else Color(0xFF757575),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (isLoggedIn) {
                                    OutlinedButton(
                                        onClick = {
                                            authStorage.clearProvider(meta.id)
                                            Toast.makeText(context, "اطلاعات حساب ${meta.displayName} پاک شد.", Toast.LENGTH_SHORT).show()
                                            refreshTrigger++
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("خروج از حساب", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Button(
                                    onClick = { activeBrowserProvider = meta },
                                    colors = ButtonDefaults.buttonColors(containerColor = meta.brandColor),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isLoggedIn) "ورود مجدد / تغییر شماره" else "ورود با شماره موبایل (مرورگر داخلی)",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Developer fallback link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualDeveloperDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ورود دستی توکن و کوکی (مخصوص توسعه‌دهندگان)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserLoginDialog(
    meta: ProviderAuthMeta,
    authStorage: AuthStorage,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(meta.loginUrl) }
    var pageTitle by remember { mutableStateOf(meta.displayName) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "ورود به ${meta.displayName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "شماره موبایل و کد پیامک را وارد کنید",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                saveSessionFromWebView(
                                    webView = currentWebView,
                                    meta = meta,
                                    authStorage = authStorage,
                                    context = context,
                                    onSuccess = onClose
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تایید و ذخیره", fontSize = 12.sp)
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = meta.brandColor
                    )
                }

                // In-App WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                currentWebView = this
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        url?.let { currentUrl = it }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        url?.let { currentUrl = it }
                                        pageTitle = view?.title ?: meta.displayName

                                        // Auto-inspect cookies
                                        val cookies = CookieManager.getInstance().getCookie(url)
                                        if (!cookies.isNullOrBlank()) {
                                            // Save cookies in background
                                            authStorage.saveCookies(meta.id, cookies)
                                        }

                                        // Auto-inspect localStorage for tokens
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                try {
                                                    for (var i = 0; i < localStorage.length; i++) {
                                                        var k = localStorage.key(i);
                                                        if (k && (k.toLowerCase().includes('token') || k.toLowerCase().includes('jwt') || k.toLowerCase().includes('auth'))) {
                                                            var val = localStorage.getItem(k);
                                                            if (val && val.length > 15) return val;
                                                        }
                                                    }
                                                } catch(e) {}
                                                return '';
                                            })();
                                            """.trimIndent()
                                        ) { tokenResult ->
                                            val cleanToken = tokenResult?.trim('"', ' ', '\'', '\\')
                                            if (!cleanToken.isNullOrBlank() && cleanToken != "null") {
                                                authStorage.saveToken(meta.id, cleanToken)
                                            }
                                        }
                                    }
                                }

                                loadUrl(meta.loginUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom Help & Action Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پس از دریافت کد پیامک و ورود به حساب، روی دکمه سبز کلیک کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                saveSessionFromWebView(
                                    webView = currentWebView,
                                    meta = meta,
                                    authStorage = authStorage,
                                    context = context,
                                    onSuccess = onClose
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("تایید ورود و ذخیره نشست ✓", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun saveSessionFromWebView(
    webView: WebView?,
    meta: ProviderAuthMeta,
    authStorage: AuthStorage,
    context: Context,
    onSuccess: () -> Unit
) {
    val url = webView?.url ?: meta.loginUrl
    val cookies = CookieManager.getInstance().getCookie(url) ?: ""

    webView?.evaluateJavascript(
        """
        (function() {
            try {
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && (k.toLowerCase().includes('token') || k.toLowerCase().includes('jwt') || k.toLowerCase().includes('auth'))) {
                        var val = localStorage.getItem(k);
                        if (val && val.length > 15) return val;
                    }
                }
            } catch(e) {}
            return '';
        })();
        """.trimIndent()
    ) { tokenResult ->
        val cleanToken = tokenResult?.trim('"', ' ', '\'', '\\')
        var savedAny = false

        if (!cookies.isBlank()) {
            authStorage.saveCookies(meta.id, cookies)
            savedAny = true
        }

        if (!cleanToken.isNullOrBlank() && cleanToken != "null") {
            authStorage.saveToken(meta.id, cleanToken)
            savedAny = true
        }

        if (savedAny) {
            Toast.makeText(context, "ورود به ${meta.displayName} با موفقیت ذخیره شد ✓", Toast.LENGTH_LONG).show()
        } else {
            // Still save URL cookies as fallback
            authStorage.saveCookies(meta.id, CookieManager.getInstance().getCookie(meta.loginUrl) ?: "")
            Toast.makeText(context, "نشست ذخیره شد.", Toast.LENGTH_SHORT).show()
        }
        onSuccess()
    }
}
