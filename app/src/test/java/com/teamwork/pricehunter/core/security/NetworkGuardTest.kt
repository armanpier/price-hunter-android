package com.teamwork.pricehunter.core.security

import org.junit.Assert.*
import org.junit.Test

class NetworkGuardTest {

    private class FakeConnectivityProvider(
        var connected: Boolean = true,
        var vpnActive: Boolean = false
    ) : ConnectivityProvider {
        override fun isConnected(): Boolean = connected
        override fun isVpnActive(): Boolean = vpnActive
    }

    @Test
    fun testConnectedWithoutVpn() {
        val fake = FakeConnectivityProvider(connected = true, vpnActive = false)
        val guard = NetworkGuard(fake)

        val status = guard.checkNetworkStatus()
        assertTrue(status is NetworkStatus.Connected)
    }

    @Test
    fun testConnectedWithVpn() {
        val fake = FakeConnectivityProvider(connected = true, vpnActive = true)
        val guard = NetworkGuard(fake)

        val status = guard.checkNetworkStatus()
        assertTrue(status is NetworkStatus.ConnectedWithVpn)
        val warning = (status as NetworkStatus.ConnectedWithVpn).warning
        assertTrue(warning.contains("فیلترشکن"))
    }

    @Test
    fun testDisconnected() {
        val fake = FakeConnectivityProvider(connected = false, vpnActive = false)
        val guard = NetworkGuard(fake)

        val status = guard.checkNetworkStatus()
        assertTrue(status is NetworkStatus.Disconnected)
    }

    @Test
    fun testDomestic403Detection() {
        val fake = FakeConnectivityProvider(connected = true, vpnActive = false)
        val guard = NetworkGuard(fake)

        assertTrue(guard.isDomesticBlockedError(403, isTimeout = false))
        assertTrue(guard.isDomesticBlockedError(451, isTimeout = false))
        assertFalse(guard.isDomesticBlockedError(500, isTimeout = false))
        assertFalse(guard.isDomesticBlockedError(200, isTimeout = false))
    }

    @Test
    fun testTimeoutWithVpnActive() {
        val fakeWithVpn = FakeConnectivityProvider(connected = true, vpnActive = true)
        val guardWithVpn = NetworkGuard(fakeWithVpn)
        assertTrue(guardWithVpn.isDomesticBlockedError(0, isTimeout = true))

        val fakeNoVpn = FakeConnectivityProvider(connected = true, vpnActive = false)
        val guardNoVpn = NetworkGuard(fakeNoVpn)
        assertFalse(guardNoVpn.isDomesticBlockedError(0, isTimeout = true))
    }

    @Test
    fun testPersianGuidanceBannerMessages() {
        val fake = FakeConnectivityProvider(connected = true, vpnActive = true)
        val guard = NetworkGuard(fake)

        val vpn403Msg = guard.getPersianGuidanceMessage(httpStatusCode = 403)
        assertTrue(vpn403Msg.contains("فیلترشکن") && vpn403Msg.contains("۴۰۳"))

        val vpnTimeoutMsg = guard.getPersianGuidanceMessage(isTimeout = true)
        assertTrue(vpnTimeoutMsg.contains("فیلترشکن") && vpnTimeoutMsg.contains("مهلت اتصال"))

        // Disconnected state message
        fake.connected = false
        val disconnectedMsg = guard.getPersianGuidanceMessage()
        assertTrue(disconnectedMsg.contains("ارتباط با اینترنت برقرار نیست"))

        // Non-VPN 403 message
        fake.connected = true
        fake.vpnActive = false
        val general403Msg = guard.getPersianGuidanceMessage(httpStatusCode = 403)
        assertTrue(general403Msg.contains("۴۰۳") && !general403Msg.contains("فیلترشکن را خاموش کنید"))
    }
}
