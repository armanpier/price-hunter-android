package com.teamwork.pricehunter.core.security

interface AuthStorage {
    fun saveToken(providerKey: String, token: String)
    fun getToken(providerKey: String): String?
    fun saveCookies(providerKey: String, cookies: String)
    fun getCookies(providerKey: String): String?
    fun clearProvider(providerKey: String)
    fun clearAll()
}
