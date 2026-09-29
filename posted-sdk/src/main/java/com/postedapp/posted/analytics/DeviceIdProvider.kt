package com.postedapp.posted.analytics

import com.postedapp.posted.storage.PostedStorage
import com.postedapp.posted.storage.StorageKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Generates and persists a UUID v4 device identifier.
 *
 * Mirrors the RN SDK's `devicePersistence.ts`. Uses a double-checked locking
 * pattern with a [Mutex] to prevent concurrent generation of different IDs.
 */
internal class DeviceIdProvider(private val storage: PostedStorage) {
    @Volatile
    private var cachedId: String? = null

    private val mutex = Mutex()

    /**
     * Get the device ID, generating and persisting one if needed.
     */
    suspend fun getDeviceId(): String {
        // Fast path: return cached value
        cachedId?.let { return it }

        return mutex.withLock {
            // Double-check after acquiring lock
            cachedId?.let { return it }

            // Try loading from storage
            val storedId = storage.getItem(StorageKeys.DEVICE_ID)
            if (storedId != null) {
                cachedId = storedId
                return storedId
            }

            // Generate new ID
            val newId = UUID.randomUUID().toString()
            storage.setItem(StorageKeys.DEVICE_ID, newId)
            cachedId = newId
            newId
        }
    }
}
