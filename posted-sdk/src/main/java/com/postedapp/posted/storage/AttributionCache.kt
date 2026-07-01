package com.postedapp.posted.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType

/**
 * Persists the fire-once attribution flag and cached PostedResult across app sessions and updates.
 *
 * Cleared on reinstall (OS wipes storage) or explicit [clear] call (resetSession).
 */
internal class AttributionCache(private val storage: PostedStorage) {
    private val gson = Gson()

    suspend fun isResolved(): Boolean = storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) == "true"

    suspend fun saveResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                storage.setItem(StorageKeys.CACHED_RESULT_KIND, KIND_SUCCESS)
                storage.setItem(StorageKeys.CACHED_RESULT_URL, result.url)
                storage.setItem(StorageKeys.CACHED_RESULT_ROUTE, result.route)
                storage.setItem(StorageKeys.CACHED_RESULT_PATHNAME, result.pathname)
                storage.setItem(StorageKeys.CACHED_RESULT_TYPE, result.type.name)
                storage.setItem(StorageKeys.CACHED_RESULT_PARAMS, gson.toJson(result.params))
            }
            is LinkResult.NoLink -> storage.setItem(StorageKeys.CACHED_RESULT_KIND, KIND_NO_LINK)
            else -> return
        }
        storage.setItem(StorageKeys.ATTRIBUTION_RESOLVED, "true")
    }

    suspend fun loadResult(): LinkResult? {
        return when (storage.getItem(StorageKeys.CACHED_RESULT_KIND)) {
            KIND_NO_LINK -> LinkResult.NoLink
            KIND_SUCCESS -> loadSuccessResult()
            else -> null
        }
    }

    suspend fun clear() {
        storage.removeItem(StorageKeys.ATTRIBUTION_RESOLVED)
        storage.removeItem(StorageKeys.CACHED_RESULT_KIND)
        storage.removeItem(StorageKeys.CACHED_RESULT_URL)
        storage.removeItem(StorageKeys.CACHED_RESULT_ROUTE)
        storage.removeItem(StorageKeys.CACHED_RESULT_PATHNAME)
        storage.removeItem(StorageKeys.CACHED_RESULT_TYPE)
        storage.removeItem(StorageKeys.CACHED_RESULT_PARAMS)
    }

    private suspend fun loadSuccessResult(): LinkResult.Success? {
        val url = storage.getItem(StorageKeys.CACHED_RESULT_URL) ?: return null
        val route = storage.getItem(StorageKeys.CACHED_RESULT_ROUTE) ?: return null
        val pathname = storage.getItem(StorageKeys.CACHED_RESULT_PATHNAME) ?: return null
        val typeStr = storage.getItem(StorageKeys.CACHED_RESULT_TYPE) ?: return null
        val type = runCatching { LinkType.valueOf(typeStr) }.getOrNull() ?: return null
        val paramsJson = storage.getItem(StorageKeys.CACHED_RESULT_PARAMS) ?: "{}"
        val paramsType = object : TypeToken<Map<String, String>>() {}.type
        val params: Map<String, String> =
            runCatching {
                gson.fromJson<Map<String, String>>(paramsJson, paramsType)
            }.getOrDefault(emptyMap())
        return LinkResult.Success(url = url, route = route, pathname = pathname, type = type, params = params)
    }

    companion object {
        private const val KIND_SUCCESS = "success"
        private const val KIND_NO_LINK = "no_link"
    }
}
