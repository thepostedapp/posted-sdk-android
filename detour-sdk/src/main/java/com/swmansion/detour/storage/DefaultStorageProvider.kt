package com.swmansion.detour.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default storage implementation using Android SharedPreferences.
 *
 * This is equivalent to React Native's `@react-native-async-storage/async-storage`.
 * It provides a simple, reliable storage mechanism that works for most use cases.
 *
 * For enhanced functionality, consider providing a custom storage implementation:
 * - **Performance**: MMKV for faster read/write operations
 * - **Security**: EncryptedSharedPreferences for sensitive data
 * - **Modern APIs**: Jetpack DataStore for type-safe storage
 * - **Integration**: Match your app's existing storage solution
 */
internal class DefaultStorageProvider(context: Context) : DetourStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    override suspend fun getItem(key: String): String? =
        withContext(Dispatchers.IO) {
            prefs.getString(key, null)
        }

    override suspend fun setItem(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(key, value).apply()
        }
    }

    override suspend fun removeItem(key: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(key).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "Detour"
    }
}
