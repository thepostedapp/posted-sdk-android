package com.postedapp.posted.storage

/**
 * Interface for custom storage providers used by posted-sdk.
 *
 * Allows providing custom storage implementations such as:
 * - MMKV (high-performance key-value storage)
 * - EncryptedSharedPreferences (secure storage)
 * - DataStore (Jetpack's modern storage solution)
 * - Room Database
 * - Any other storage mechanism
 *
 * Example with SharedPreferences:
 * ```kotlin
 * class MyStorageProvider(context: Context) : PostedStorage {
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
interface PostedStorage {
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
     * Store multiple string values in a single atomic commit.
     *
     * The default implementation falls back to sequential [setItem] calls, which is safe but not
     * atomic. Override with a single-commit batch (e.g. one `SharedPreferences.edit().apply()`)
     * if you need crash-proof atomicity for fire-once cache writes.
     *
     * @param entries Key-value pairs to store
     */
    suspend fun setItems(entries: Map<String, String>) {
        entries.forEach { (key, value) -> setItem(key, value) }
    }

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

    /**
     * Remove multiple values from storage in a single atomic commit.
     *
     * The default implementation falls back to sequential [removeItem] calls, which is safe but not
     * atomic. Override with a single-commit batch (e.g. one `SharedPreferences.edit().apply()`) if
     * you need crash-proof atomicity — e.g. clearing a fire-once cache without a torn partial clear.
     *
     * @param keys Storage keys to remove
     */
    suspend fun removeItems(keys: Set<String>) {
        keys.forEach { key -> removeItem(key) }
    }
}
