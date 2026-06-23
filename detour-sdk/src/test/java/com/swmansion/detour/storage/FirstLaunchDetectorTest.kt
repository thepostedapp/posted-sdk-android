package com.swmansion.detour.storage

import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirstLaunchDetectorTest {
    private lateinit var storage: InMemoryStorage
    private lateinit var detector: FirstLaunchDetector

    @Before
    fun setUp() {
        storage = InMemoryStorage()
        detector = FirstLaunchDetector(storage)
    }

    @Test
    fun `isFirstLaunch returns true on fresh storage`() =
        runTest {
            assertTrue(detector.isFirstLaunch())
        }

    @Test
    fun `isFirstLaunch returns false after markAsLaunched`() =
        runTest {
            detector.markAsLaunched()
            assertFalse(detector.isFirstLaunch())
        }

    @Test
    fun `isFirstLaunch returns true when flag is not the string true`() =
        runTest {
            storage.setItem(StorageKeys.FIRST_ENTRANCE_FLAG, "false")
            assertTrue(detector.isFirstLaunch())
        }
}
