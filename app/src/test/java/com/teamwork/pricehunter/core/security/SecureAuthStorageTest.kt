package com.teamwork.pricehunter.core.security

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class SecureAuthStorageTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val storage = ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(storage)

        override fun getString(key: String?, defValue: String?): String? {
            return storage[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return storage[key] as? MutableSet<String> ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return storage[key] as? Int ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return storage[key] as? Long ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return storage[key] as? Float ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return storage[key] as? Boolean ?: defValue
        }

        override fun contains(key: String?): Boolean = storage.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val storage: ConcurrentHashMap<String, Any>) : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private var clearPending = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearPending = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearPending) {
                    storage.clear()
                }
                for ((k, v) in pending) {
                    if (v == null) {
                        storage.remove(k)
                    } else {
                        storage[k] = v
                    }
                }
            }
        }
    }

    private lateinit var secureStorage: SecureAuthStorage
    private lateinit var inMemoryStorage: InMemoryAuthStorage

    @Before
    fun setUp() {
        val fakePrefs = FakeSharedPreferences()
        secureStorage = SecureAuthStorage(fakePrefs)
        inMemoryStorage = InMemoryAuthStorage()
    }

    @Test
    fun testTokenStorageAndRetrieval() {
        secureStorage.saveToken("digikala", "sample_digikala_token_12345")
        assertEquals("sample_digikala_token_12345", secureStorage.getToken("digikala"))
        assertNull(secureStorage.getToken("torob"))

        inMemoryStorage.saveToken("digikala", "sample_digikala_token_12345")
        assertEquals("sample_digikala_token_12345", inMemoryStorage.getToken("digikala"))
        assertNull(inMemoryStorage.getToken("torob"))
    }

    @Test
    fun testCookieStorageAndRetrieval() {
        val testCookies = "session_id=abc123xyz; secure; HttpOnly; Path=/"
        secureStorage.saveCookies("snappfood", testCookies)
        assertEquals(testCookies, secureStorage.getCookies("snappfood"))

        inMemoryStorage.saveCookies("snappfood", testCookies)
        assertEquals(testCookies, inMemoryStorage.getCookies("snappfood"))
    }

    @Test
    fun testProviderIsolation() {
        secureStorage.saveToken("snappfood", "token_snappfood")
        secureStorage.saveCookies("snappfood", "cookie_snappfood")
        secureStorage.saveToken("tapsifood", "token_tapsifood")
        secureStorage.saveCookies("tapsifood", "cookie_tapsifood")

        secureStorage.clearProvider("snappfood")

        assertNull(secureStorage.getToken("snappfood"))
        assertNull(secureStorage.getCookies("snappfood"))
        assertEquals("token_tapsifood", secureStorage.getToken("tapsifood"))
        assertEquals("cookie_tapsifood", secureStorage.getCookies("tapsifood"))
    }

    @Test
    fun testClearAll() {
        secureStorage.saveToken("p1", "tok1")
        secureStorage.saveCookies("p2", "cook2")
        secureStorage.clearAll()

        assertNull(secureStorage.getToken("p1"))
        assertNull(secureStorage.getCookies("p2"))

        inMemoryStorage.saveToken("p1", "tok1")
        inMemoryStorage.saveCookies("p2", "cook2")
        inMemoryStorage.clearAll()

        assertNull(inMemoryStorage.getToken("p1"))
        assertNull(inMemoryStorage.getCookies("p2"))
    }
}
