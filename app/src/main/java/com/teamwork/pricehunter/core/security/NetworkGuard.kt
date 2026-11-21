package com.teamwork.pricehunter.core.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface ConnectivityProvider {
    fun isConnected(): Boolean
    fun isVpnActive(): Boolean
}

class AndroidConnectivityProvider(
    private val context: Context
) : ConnectivityProvider {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isVpnActive(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}

class NetworkGuard(
    private val connectivityProvider: ConnectivityProvider
) : NetworkMonitor {

    constructor(context: Context) : this(AndroidConnectivityProvider(context))

    companion object {
        const val PERSIAN_VPN_WARNING =
            "فیلترشکن شما روشن است. دسترسی به برخی سرویس‌های داخلی (اسنپ‌فود، تپسی‌فود و ...) ممکن است مسدود شود یا با خطا مواجه گردد."

        const val PERSIAN_MSG_DISCONNECTED =
            "ارتباط با اینترنت برقرار نیست. لطفاً اتصال شبکه خود را بررسی نمایید."

        const val PERSIAN_MSG_403_VPN_BLOCKED =
            "خطای دسترسی (کد ۴۰۳): سرور این سرویس به دلیل روشن بودن فیلترشکن، درخواست شما را مسدود کرده است. لطفاً فیلترشکن را خاموش کنید."

        const val PERSIAN_MSG_403_GENERAL =
            "خطای دسترسی (کد ۴۰۳): دسترسی به این سرویس موقتاً مسدود است یا نیاز به ورود مجدد دارد."

        const val PERSIAN_MSG_TIMEOUT_VPN =
            "پایان مهلت اتصال: سرور پاسخ نداد. فعال بودن فیلترشکن ممکن است ارتباط با سرورهای داخلی را قطع کرده باشد."

        const val PERSIAN_MSG_TIMEOUT_GENERAL =
            "پایان مهلت اتصال: پاسخی از سرور دریافت نشد. لطفاً مجدداً تلاش نمایید."
    }

    override fun checkNetworkStatus(): NetworkStatus {
        if (!connectivityProvider.isConnected()) {
            return NetworkStatus.Disconnected
        }
        if (connectivityProvider.isVpnActive()) {
            return NetworkStatus.ConnectedWithVpn(PERSIAN_VPN_WARNING)
        }
        return NetworkStatus.Connected
    }

    override fun isDomesticBlockedError(httpStatusCode: Int, isTimeout: Boolean): Boolean {
        if (httpStatusCode == 403 || httpStatusCode == 451) {
            return true
        }
        if (isTimeout && connectivityProvider.isVpnActive()) {
            return true
        }
        return false
    }

    fun getPersianGuidanceMessage(httpStatusCode: Int = 0, isTimeout: Boolean = false): String {
        if (!connectivityProvider.isConnected()) {
            return PERSIAN_MSG_DISCONNECTED
        }
        val isVpn = connectivityProvider.isVpnActive()
        return when {
            (httpStatusCode == 403 || httpStatusCode == 451) && isVpn -> PERSIAN_MSG_403_VPN_BLOCKED
            (httpStatusCode == 403 || httpStatusCode == 451) -> PERSIAN_MSG_403_GENERAL
            isTimeout && isVpn -> PERSIAN_MSG_TIMEOUT_VPN
            isTimeout -> PERSIAN_MSG_TIMEOUT_GENERAL
            isVpn -> PERSIAN_VPN_WARNING
            else -> "خطایی در برقراری ارتباط رخ داده است."
        }
    }
}
