package com.teamwork.pricehunter.core.security

sealed class NetworkStatus {
    object Connected : NetworkStatus()
    data class ConnectedWithVpn(val warning: String) : NetworkStatus()
    object Disconnected : NetworkStatus()
}

interface NetworkMonitor {
    fun checkNetworkStatus(): NetworkStatus
    fun isDomesticBlockedError(httpStatusCode: Int, isTimeout: Boolean): Boolean
}
