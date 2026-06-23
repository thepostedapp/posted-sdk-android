package com.postedapp.posted.example.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.postedapp.posted.storage.PostedStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Example encrypted storage implementation using Android's EncryptedSharedPreferences.
 *
 * This demonstrates how to create a secure custom storage provider for posted-sdk.
 * All data is encrypted at rest using AES256_GCM encryption.
 *
 * Usage:
 * ```
 * val config = PostedConfig(
 *     apiKey = "your-api-key",
 *     appId = "your-app-id",
 *     storage = EncryptedStorageProvider(context)
 * )
 * ```
 */
class EncryptedStorageProvider(context: Context) : PostedStorage {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getItem(key: String): String? =
        withContext(Dispatchers.IO) {
            prefs.getString(key, null)
        }

    override suspend fun setItem(key: String, value: String) {
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
        private const val PREFS_NAME = "PostedSecureStorage"
    }
}
