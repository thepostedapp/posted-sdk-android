package com.postedapp.posted.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultStorageProviderTest {
    private val provider = DefaultStorageProvider(RuntimeEnvironment.getApplication())

    @Test
    fun `getItem returns null for an unknown key`() =
        runTest {
            assertNull(provider.getItem("missing"))
        }

    @Test
    fun `setItem persists and getItem reads it back`() =
        runTest {
            provider.setItem("a", "1")
            assertEquals("1", provider.getItem("a"))

            provider.setItem("a", "2")
            assertEquals("2", provider.getItem("a"))
        }
}
