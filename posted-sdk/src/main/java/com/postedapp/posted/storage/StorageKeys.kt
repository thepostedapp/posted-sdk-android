package com.postedapp.posted.storage

/**
 * Shared storage key constants used by the SDK.
 */
internal object StorageKeys {
    const val FIRST_ENTRANCE_FLAG = "Posted_firstEntranceFlag"
    const val DEVICE_ID = "Posted_deviceId"

    // Fire-once attribution cache (resolveAppLaunch)
    const val ATTRIBUTION_RESOLVED = "Posted_attributionResolved"
    const val CACHED_RESULT_KIND = "Posted_cachedResultKind"
    const val CACHED_RESULT_URL = "Posted_cachedResultUrl"
    const val CACHED_RESULT_ROUTE = "Posted_cachedResultRoute"
    const val CACHED_RESULT_PATHNAME = "Posted_cachedResultPathname"
    const val CACHED_RESULT_TYPE = "Posted_cachedResultType"
    const val CACHED_RESULT_PARAMS = "Posted_cachedResultParams"
}
