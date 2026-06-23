package com.swmansion.detour.test

import com.swmansion.detour.storage.DetourStorage

/**
 * In-memory storage implementation for unit tests.
 */
class InMemoryStorage : DetourStorage {
    private val store = mutableMapOf<String, String>()

    override suspend fun getItem(key: String): String? = store[key]

    override suspend fun setItem(
        key: String,
        value: String,
    ) {
        store[key] = value
    }

    override suspend fun removeItem(key: String) {
        store.remove(key)
    }

    fun clear() {
        store.clear()
    }
}
