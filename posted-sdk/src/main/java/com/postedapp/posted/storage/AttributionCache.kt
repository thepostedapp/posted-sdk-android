package com.postedapp.posted.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType

/**
 * Persists the fire-once attribution flag and cached PostedResult across app sessions and updates.
 *
 * Cleared on reinstall (OS wipes storage) or explicit [clear] call (resetSession).
 *
 * All cache writes are atomic (single [PostedStorage.setItems] commit). A [CURRENT_CACHE_SCHEMA]
 * version key is written alongside each record. On read, a missing or mismatched version triggers a
 * clear + re-resolve — EXCEPT for legacy records (schema 0: flag+blob present, no version key),
 * which are loaded as-is and opportunistically re-stamped to preserve fire-once across app updates.
 */
internal class AttributionCache(private val storage: PostedStorage) {
    private val gson = Gson()

    suspend fun isResolved(): Boolean {
        val flag = storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) ?: return false
        if (flag != "true") return false
        return schemaCheck()
    }

    suspend fun saveResult(result: LinkResult) {
        val entries =
            when (result) {
                is LinkResult.Success ->
                    mapOf(
                        StorageKeys.CACHED_RESULT_KIND to KIND_SUCCESS,
                        StorageKeys.CACHED_RESULT_URL to result.url,
                        StorageKeys.CACHED_RESULT_ROUTE to result.route,
                        StorageKeys.CACHED_RESULT_PATHNAME to result.pathname,
                        StorageKeys.CACHED_RESULT_TYPE to result.type.name,
                        StorageKeys.CACHED_RESULT_PARAMS to gson.toJson(result.params),
                        StorageKeys.ATTRIBUTION_RESOLVED to "true",
                        StorageKeys.CACHE_SCHEMA_VERSION to CURRENT_CACHE_SCHEMA.toString(),
                    )
                is LinkResult.NoLink ->
                    mapOf(
                        StorageKeys.CACHED_RESULT_KIND to KIND_NO_LINK,
                        StorageKeys.ATTRIBUTION_RESOLVED to "true",
                        StorageKeys.CACHE_SCHEMA_VERSION to CURRENT_CACHE_SCHEMA.toString(),
                    )
                else -> return
            }
        storage.setItems(entries)
    }

    suspend fun loadResult(): LinkResult? {
        val flag = storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) ?: return null
        if (flag != "true") return null
        if (!schemaCheck()) return null
        return when (storage.getItem(StorageKeys.CACHED_RESULT_KIND)) {
            KIND_NO_LINK -> LinkResult.NoLink
            KIND_SUCCESS -> loadSuccessResult()
            else -> null
        }
    }

    suspend fun clear() {
        storage.removeItem(StorageKeys.ATTRIBUTION_RESOLVED)
        storage.removeItem(StorageKeys.CACHE_SCHEMA_VERSION)
        storage.removeItem(StorageKeys.CACHED_RESULT_KIND)
        storage.removeItem(StorageKeys.CACHED_RESULT_URL)
        storage.removeItem(StorageKeys.CACHED_RESULT_ROUTE)
        storage.removeItem(StorageKeys.CACHED_RESULT_PATHNAME)
        storage.removeItem(StorageKeys.CACHED_RESULT_TYPE)
        storage.removeItem(StorageKeys.CACHED_RESULT_PARAMS)
    }

    /**
     * Returns true if the persisted cache schema is compatible with the current version.
     *
     * Rules:
     * - Version key present + matches [CURRENT_CACHE_SCHEMA] → valid.
     * - Version key present + mismatch → wipe + return false (future/corrupt schema).
     * - Version key absent + blob fields complete → legacy schema 0; load + re-stamp.
     * - Version key absent + blob fields incomplete → torn write; wipe + return false.
     */
    private suspend fun schemaCheck(): Boolean {
        val stored = storage.getItem(StorageKeys.CACHE_SCHEMA_VERSION)
        if (stored != null) {
            return stored.toIntOrNull() == CURRENT_CACHE_SCHEMA
        }

        // Version absent — check for a legacy (pre-POS-161) record.
        val kind = storage.getItem(StorageKeys.CACHED_RESULT_KIND)
        val legacyValid =
            when (kind) {
                KIND_NO_LINK -> true
                KIND_SUCCESS -> isSuccessBlobComplete()
                else -> false
            }

        if (legacyValid) {
            // Opportunistically re-stamp so future reads skip this migration path.
            storage.setItem(StorageKeys.CACHE_SCHEMA_VERSION, CURRENT_CACHE_SCHEMA.toString())
            return true
        }

        // Torn or corrupt — clear and signal re-resolve.
        clear()
        return false
    }

    private suspend fun isSuccessBlobComplete(): Boolean =
        storage.getItem(StorageKeys.CACHED_RESULT_URL) != null &&
            storage.getItem(StorageKeys.CACHED_RESULT_ROUTE) != null &&
            storage.getItem(StorageKeys.CACHED_RESULT_PATHNAME) != null &&
            storage.getItem(StorageKeys.CACHED_RESULT_TYPE) != null

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
        const val CURRENT_CACHE_SCHEMA = 1
    }
}
