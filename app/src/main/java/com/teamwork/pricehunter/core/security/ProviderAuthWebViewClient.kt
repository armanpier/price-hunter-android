package com.teamwork.pricehunter.core.security

interface SslErrorHandlerCompat {
    fun proceed()
    fun cancel()
}

class ProviderAuthWebViewClient(
    private val authStorage: AuthStorage,
    private val providerKey: String,
    private val onCookieCaptured: ((String) -> Unit)? = null
) {
    var lastSslErrorCancelled: Boolean = false
        private set
    var lastSslProceeded: Boolean = false
        private set

    fun onReceivedSslError(handler: SslErrorHandlerCompat, failingUrl: String?, primaryError: Int) {
        // Zero-knowledge security guarantee: ALWAYS cancel on SSL error to protect credentials
        handler.cancel()
        lastSslErrorCancelled = true
        lastSslProceeded = false
    }

    fun onCookieIntercepted(cookies: String) {
        authStorage.saveCookies(providerKey, cookies)
        onCookieCaptured?.invoke(cookies)
    }
}
