package com.postedapp.posted.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Generates and persists a random, per-install device id. Reinstalling clears it.
 */
internal class DeviceIdProvider(private val storage: PostedStorage) {
    @Volatile
    private var cachedId: String? = null

    private val mutex = Mutex()

    suspend fun getDeviceId(): String {
        cachedId?.let { return it }

        return mutex.withLock {
            cachedId?.let { return it }

            val storedId = storage.getItem(StorageKeys.DEVICE_ID)
            if (storedId != null) {
                cachedId = storedId
                return storedId
            }

            val newId = UUID.randomUUID().toString()
            storage.setItem(StorageKeys.DEVICE_ID, newId)
            cachedId = newId
            newId
        }
    }
}
