package com.teamwork.pricehunter.core.security

import java.util.concurrent.ConcurrentHashMap

class InMemoryAuthStorage : AuthStorage {
    private val tokens = ConcurrentHashMap<String, String>()
    private val cookies = ConcurrentHashMap<String, String>()

    override fun saveToken(providerKey: String, token: String) {
        tokens[providerKey] = token
    }

    override fun getToken(providerKey: String): String? = tokens[providerKey]

    override fun saveCookies(providerKey: String, cookies: String) {
        this.cookies[providerKey] = cookies
    }

    override fun getCookies(providerKey: String): String? = this.cookies[providerKey]

    override fun clearProvider(providerKey: String) {
        tokens.remove(providerKey)
        cookies.remove(providerKey)
    }

    override fun clearAll() {
        tokens.clear()
        cookies.clear()
    }
}
