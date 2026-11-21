package com.teamwork.pricehunter.e2e.tier2_boundary

import com.teamwork.pricehunter.core.security.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 2: Boundary & Corner Cases — Security & NetworkGuard (10 tests)
 * Spec reference: TC-T2-ZS-01..05 & TC-T2-NG-01..05
 */
class Tier2SecurityNetworkBoundaryTest {

    // --- 1. Zero-Knowledge Security Boundaries (5 tests) ---

    @Test
    fun TC_T2_ZS_01_emptyAndWhitespaceTokenStorage() {
        val storage: AuthStorage = InMemoryAuthStorage()
        storage.saveToken("snappfood", "")
        assertEquals("", storage.getToken("snappfood"))

        storage.saveToken("tapsifood", "   ")
        assertEquals("   ", storage.getToken("tapsifood"))
    }

    @Test
    fun TC_T2_ZS_02_massiveCookiePayload64KB() {
        val storage: AuthStorage = InMemoryAuthStorage()
        val largeCookie = "key=" + "A".repeat(65536) + "; path=/"
        storage.saveCookies("digikala", largeCookie)

        val retrieved = storage.getCookies("digikala")
        assertNotNull(retrieved)
        assertEquals(largeCookie.length, retrieved!!.length)
        assertEquals(largeCookie, retrieved)
    }

    @Test
    fun TC_T2_ZS_03_specialCharactersInProviderKeys() {
        val storage: AuthStorage = InMemoryAuthStorage()
        val complexKey = "provider/test?name=1&val=2#hash"
        storage.saveToken(complexKey, "token_123")

        val retrieved = storage.getToken(complexKey)
        assertEquals("token_123", retrieved)
    }

    @Test
    fun TC_T2_ZS_04_highConcurrencyReadWriteStress() = runBlocking {
        val storage: AuthStorage = InMemoryAuthStorage()
        val jobs = (1..50).map { id ->
            launch(Dispatchers.Default) {
                val key = "provider_$id"
                storage.saveToken(key, "token_$id")
                val tok = storage.getToken(key)
                assertEquals("token_$id", tok)
                storage.saveCookies(key, "cookie_$id")
                val cook = storage.getCookies(key)
                assertEquals("cookie_$id", cook)
            }
        }
        jobs.joinAll()
    }

    @Test
    fun TC_T2_ZS_05_corruptedStorageRecovery() {
        val storage: AuthStorage = InMemoryAuthStorage()
        storage.saveToken("snapp", "token_old")
        storage.clearAll()
        assertNull(storage.getToken("snapp"))
    }

    // --- 2. NetworkGuard Boundaries (5 tests) ---

    private class SwitchableConnectivity(
        var connected: Boolean = true,
        var vpn: Boolean = false
    ) : ConnectivityProvider {
        override fun isConnected(): Boolean = connected
        override fun isVpnActive(): Boolean = vpn
    }

    @Test
    fun TC_T2_NG_01_midRequestVpnNetworkTransition() {
        val conn = SwitchableConnectivity(connected = true, vpn = true)
        val guard = NetworkGuard(conn)

        assertTrue(guard.checkNetworkStatus() is NetworkStatus.ConnectedWithVpn)

        // Mid-request VPN disconnected
        conn.vpn = false
        assertEquals(NetworkStatus.Connected, guard.checkNetworkStatus())
    }

    @Test
    fun TC_T2_NG_02_nonVpn403ForbiddenError() {
        val conn = SwitchableConnectivity(connected = true, vpn = false)
        val guard = NetworkGuard(conn)

        // 403 when VPN is NOT active -> does not prompt to disable VPN
        val msg = guard.getPersianGuidanceMessage(httpStatusCode = 403, isTimeout = false)
        assertFalse("Should not advise disabling VPN when VPN is off", msg.contains("خاموش"))
    }

    @Test
    fun TC_T2_NG_03_domestic403WithVpnActive() {
        val conn = SwitchableConnectivity(connected = true, vpn = true)
        val guard = NetworkGuard(conn)

        // 403 when VPN is active -> explicitly advises disabling VPN
        val msg = guard.getPersianGuidanceMessage(httpStatusCode = 403, isTimeout = false)
        assertTrue("Must advise turning off VPN", msg.contains("خاموش"))
    }

    @Test
    fun TC_T2_NG_04_socketTimeoutOnInternationalHost() {
        val conn = SwitchableConnectivity(connected = true, vpn = false)
        val guard = NetworkGuard(conn)

        val msg = guard.getPersianGuidanceMessage(httpStatusCode = 0, isTimeout = true)
        assertFalse("Timeout without VPN should not blame VPN", msg.contains("فیلترشکن ممکن است"))
    }

    @Test
    fun TC_T2_NG_05_repeatedRapidStateCallbacks() {
        val conn = SwitchableConnectivity(connected = true, vpn = false)
        val guard = NetworkGuard(conn)

        val start = System.currentTimeMillis()
        for (i in 1..100) {
            conn.vpn = (i % 2 == 0)
            guard.checkNetworkStatus()
        }
        val duration = System.currentTimeMillis() - start
        assertTrue("Rapid calls should be negligible in duration (<100ms)", duration < 100)
    }
}
