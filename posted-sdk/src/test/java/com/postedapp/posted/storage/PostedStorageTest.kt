package com.postedapp.posted.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PostedStorageTest {
    @Test
    fun `default removeItems falls back to sequential removeItem calls - POS-346`() =
        runTest {
            val removed = mutableListOf<String>()
            val storage =
                object : PostedStorage {
                    override suspend fun getItem(key: String): String? = null

                    override suspend fun setItem(
                        key: String,
                        value: String,
                    ) {}

                    override suspend fun removeItem(key: String) {
                        removed.add(key)
                    }
                }

            storage.removeItems(setOf("a", "b", "c"))

            assertEquals(listOf("a", "b", "c"), removed)
        }
}
