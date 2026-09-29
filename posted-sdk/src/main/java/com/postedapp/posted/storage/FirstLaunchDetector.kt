package com.postedapp.posted.storage

/**
 * Detects if this is the first app launch using a configurable storage implementation.
 *
 * Storage key used: `Posted_firstEntranceFlag`
 */
internal class FirstLaunchDetector(private val storage: PostedStorage) {
    /**
     * Check if this is the first app launch.
     */
    suspend fun isFirstLaunch(): Boolean {
        val value = storage.getItem(StorageKeys.FIRST_ENTRANCE_FLAG)
        return value != "true"
    }

    /**
     * Mark that the app has been launched.
     */
    suspend fun markAsLaunched() {
        storage.setItem(StorageKeys.FIRST_ENTRANCE_FLAG, "true")
    }
}
