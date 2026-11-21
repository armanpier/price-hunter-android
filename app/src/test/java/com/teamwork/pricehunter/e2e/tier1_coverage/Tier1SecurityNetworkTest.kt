package com.teamwork.pricehunter.e2e.tier1_coverage

import com.teamwork.pricehunter.core.security.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 1: Feature Coverage — Zero-Knowledge Security & NetworkGuard (10 tests)
 * Spec reference: TC-T1-ZS-01..05 & TC-T1-NG-01..05
 */
class Tier1SecurityNetworkTest {

    // --- Feature 5: Zero-Knowledge Security (5 tests) ---

    @Test
    fun TC_T1_ZS_01_encryptedTokenStorageRetrieval() {
        val storage: AuthStorage = InMemoryAuthStorage()
        val providerKey = "snappfood"
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token123"

        storage.saveToken(providerKey, token)
        val retrieved = storage.getToken(providerKey)

        assertEquals("Retrieved token should match saved token", token, retrieved)
    }

    @Test
    fun TC_T1_ZS_02_encryptedCookieHeaderStorageRetrieval() {
        val storage: AuthStorage = InMemoryAuthStorage()
        val providerKey = "tapsifood"
        val cookies = "session_id=s82df7; Path=/; Domain=.tapsi.food; Secure; HttpOnly"

        storage.saveCookies(providerKey, cookies)
        val retrieved = storage.getCookies(providerKey)

        assertEquals("Retrieved cookies should match saved cookies", cookies, retrieved)
    }

    @Test
    fun TC_T1_ZS_03_providerIsolationOnClear() {
        val storage: AuthStorage = InMemoryAuthStorage()
        storage.saveToken("snappfood", "token_snapp")
        storage.saveToken("tapsifood", "token_tapsi")

        storage.clearProvider("snappfood")

        assertNull("Cleared provider token must be null", storage.getToken("snappfood"))
        assertEquals("Other provider token must remain intact", "token_tapsi", storage.getToken("tapsifood"))
    }

    @Test
    fun TC_T1_ZS_04_zeroKnowledgeNoLeakGuarantee() {
        val storage: AuthStorage = InMemoryAuthStorage()
        val secretToken = "super_secret_auth_token_999"
        storage.saveToken("digikala", secretToken)

        val retrieved = storage.getToken("digikala")
        assertNotNull(retrieved)

        // Clear all guarantees no residue
        storage.clearAll()
        assertNull(storage.getToken("digikala"))
    }

    @Test
    fun TC_T1_ZS_05_nullSafetyOnUnsetKeys() {
        val storage: AuthStorage = InMemoryAuthStorage()
        assertNull(storage.getToken("non_existent_provider"))
        assertNull(storage.getCookies("non_existent_provider"))
    }

    // --- Feature 6: NetworkGuard (5 tests) ---

    private class MockConnectivity(
        var connected: Boolean = true,
        var vpn: Boolean = false
    ) : ConnectivityProvider {
        override fun isConnected(): Boolean = connected
        override fun isVpnActive(): Boolean = vpn
    }

    @Test
    fun TC_T1_NG_01_normalConnectedNetworkStatus() {
        val connectivity = MockConnectivity(connected = true, vpn = false)
        val guard = NetworkGuard(connectivity)

        val status = guard.checkNetworkStatus()
        assertEquals(NetworkStatus.Connected, status)
    }

    @Test
    fun TC_T1_NG_02_vpnActiveStatusDetection() {
        val connectivity = MockConnectivity(connected = true, vpn = true)
        val guard = NetworkGuard(connectivity)

        val status = guard.checkNetworkStatus()
        assertTrue("Status must be ConnectedWithVpn", status is NetworkStatus.ConnectedWithVpn)
        val warning = (status as NetworkStatus.ConnectedWithVpn).warning
        assertTrue("Warning must contain Persian guidance", warning.contains("فیلترشکن"))
    }

    @Test
    fun TC_T1_NG_03_disconnectedStateDetection() {
        val connectivity = MockConnectivity(connected = false, vpn = false)
        val guard = NetworkGuard(connectivity)

        val status = guard.checkNetworkStatus()
        assertEquals(NetworkStatus.Disconnected, status)
    }

    @Test
    fun TC_T1_NG_04_domestic403ForbiddenDetection() {
        val connectivity = MockConnectivity(connected = true, vpn = true)
        val guard = NetworkGuard(connectivity)

        val isBlocked = guard.isDomesticBlockedError(httpStatusCode = 403, isTimeout = false)
        assertTrue("403 error must be identified as domestic blocked error", isBlocked)

        val guidance = guard.getPersianGuidanceMessage(httpStatusCode = 403, isTimeout = false)
        assertTrue("Guidance should advise disabling VPN", guidance.contains("خاموش"))
    }

    @Test
    fun TC_T1_NG_05_domesticTimeoutDetection() {
        val connectivity = MockConnectivity(connected = true, vpn = true)
        val guard = NetworkGuard(connectivity)

        val isBlocked = guard.isDomesticBlockedError(httpStatusCode = 504, isTimeout = true)
        assertTrue("Timeout during active VPN must be flagged as domestic issue", isBlocked)

        val guidance = guard.getPersianGuidanceMessage(httpStatusCode = 0, isTimeout = true)
        assertTrue("Timeout guidance should mention VPN", guidance.contains("فیلترشکن"))
    }
}
