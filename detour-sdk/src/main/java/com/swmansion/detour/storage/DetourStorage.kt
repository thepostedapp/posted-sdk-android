package com.swmansion.detour.storage

/**
 * Interface for custom storage providers used by Detour SDK.
 *
 * This interface mirrors the React Native SDK's `DetourStorage` interface,
 * allowing you to provide custom storage implementations such as:
 * - MMKV (high-performance key-value storage)
 * - EncryptedSharedPreferences (secure storage)
 * - DataStore (Jetpack's modern storage solution)
 * - Room Database
 * - Any other storage mechanism
 *
 * Example with SharedPreferences:
 * ```kotlin
 * class MyStorageProvider(context: Context) : DetourStorage {
 *     private val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
 *
 *     override suspend fun getItem(key: String): String? {
 *         return withContext(Dispatchers.IO) {
 *             prefs.getString(key, null)
 *         }
 *     }
 *
 *     override suspend fun setItem(key: String, value: String) {
 *         withContext(Dispatchers.IO) {
 *             prefs.edit().putString(key, value).apply()
 *         }
 *     }
 *
 *     override suspend fun removeItem(key: String) {
 *         withContext(Dispatchers.IO) {
 *             prefs.edit().remove(key).apply()
 *         }
 *     }
 * }
 * ```
 */
interface DetourStorage {
    /**
     * Retrieve a string value from storage.
     *
     * @param key Storage key
     * @return Stored string value or null if key doesn't exist
     */
    suspend fun getItem(key: String): String?

    /**
     * Store a string value.
     *
     * @param key Storage key
     * @param value String value to store
     */
    suspend fun setItem(
        key: String,
        value: String,
    )

    /**
     * Remove a value from storage.
     * Optional - not required for SDK functionality.
     *
     * @param key Storage key to remove
     */
    suspend fun removeItem(key: String) {
        // Default implementation does nothing
        // Override if your storage needs cleanup
    }
}
