package com.teamwork.pricehunter.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Zero-knowledge encrypted storage for provider tokens and cookies.
 * Backed by Android Keystore AES256_GCM and EncryptedSharedPreferences.
 */
class SecureAuthStorage(
    private val preferences: SharedPreferences
) : AuthStorage {

    companion object {
        const val PREFS_FILE_NAME = "pricehunter_secure_auth_prefs"
        private const val PREFIX_TOKEN = "token_"
        private const val PREFIX_COOKIES = "cookies_"

        fun create(context: Context): SecureAuthStorage {
            val prefs = try {
                buildEncryptedPrefs(context)
            } catch (e: Exception) {
                // Self-healing recovery for corrupted Keystore or backup restore issues
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                buildEncryptedPrefs(context)
            }
            return SecureAuthStorage(prefs)
        }

        private fun buildEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    override fun saveToken(providerKey: String, token: String) {
        preferences.edit()
            .putString("$PREFIX_TOKEN$providerKey", token)
            .apply()
    }

    override fun getToken(providerKey: String): String? {
        return preferences.getString("$PREFIX_TOKEN$providerKey", null)
    }

    override fun saveCookies(providerKey: String, cookies: String) {
        preferences.edit()
            .putString("$PREFIX_COOKIES$providerKey", cookies)
            .apply()
    }

    override fun getCookies(providerKey: String): String? {
        return preferences.getString("$PREFIX_COOKIES$providerKey", null)
    }

    override fun clearProvider(providerKey: String) {
        preferences.edit()
            .remove("$PREFIX_TOKEN$providerKey")
            .remove("$PREFIX_COOKIES$providerKey")
            .apply()
    }

    override fun clearAll() {
        preferences.edit().clear().apply()
    }
}
