package com.postedapp.posted.test

import com.postedapp.posted.storage.PostedStorage

/**
 * In-memory storage implementation for unit tests.
 */
class InMemoryStorage : PostedStorage {
    private val store = mutableMapOf<String, String>()

    override suspend fun getItem(key: String): String? = store[key]

    override suspend fun setItem(
        key: String,
        value: String,
    ) {
        store[key] = value
    }

    fun clear() {
        store.clear()
    }
}
