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
    fun `removeItems removes all specified keys and leaves others intact - POS-346`() =
        runTest {
            provider.setItems(mapOf("a" to "1", "b" to "2", "c" to "3"))

            provider.removeItems(setOf("a", "b"))

            assertNull(provider.getItem("a"))
            assertNull(provider.getItem("b"))
            assertEquals("3", provider.getItem("c"))
        }

    @Test
    fun `removeItems on empty set is a no-op`() =
        runTest {
            provider.setItem("a", "1")

            provider.removeItems(emptySet())

            assertEquals("1", provider.getItem("a"))
        }
}
