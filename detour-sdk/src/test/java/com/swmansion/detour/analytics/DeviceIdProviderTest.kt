package com.swmansion.detour.analytics

import com.swmansion.detour.storage.StorageKeys
import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceIdProviderTest {
    private lateinit var storage: InMemoryStorage
    private lateinit var provider: DeviceIdProvider

    @Before
    fun setUp() {
        storage = InMemoryStorage()
        provider = DeviceIdProvider(storage)
    }

    @Test
    fun `getDeviceId generates a valid UUID`() =
        runTest {
            val id = provider.getDeviceId()
            assertNotNull(id)
            // UUID v4 format: 8-4-4-4-12 hex chars
            assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        }

    @Test
    fun `getDeviceId returns same value on second call`() =
        runTest {
            val first = provider.getDeviceId()
            val second = provider.getDeviceId()
            assertEquals(first, second)
        }

    @Test
    fun `getDeviceId loads existing value from storage`() =
        runTest {
            val existingId = "existing-device-id-123"
            storage.setItem(StorageKeys.DEVICE_ID, existingId)

            val id = provider.getDeviceId()
            assertEquals(existingId, id)
        }

    @Test
    fun `getDeviceId stores generated value`() =
        runTest {
            val id = provider.getDeviceId()
            val storedId = storage.getItem(StorageKeys.DEVICE_ID)
            assertEquals(id, storedId)
        }
}
