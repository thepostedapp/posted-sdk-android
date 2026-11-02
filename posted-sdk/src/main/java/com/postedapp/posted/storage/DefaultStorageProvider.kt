package com.postedapp.posted.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default storage backed by `SharedPreferences`.
 */
internal class DefaultStorageProvider(context: Context) : PostedStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    companion object {
        // Persisted on existing installs. Never rename.
        private const val PREFS_NAME = "posted-sdk"
    }
}
