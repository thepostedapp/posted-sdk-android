package com.postedapp.posted.storage

/**
 * Persistent key-value storage used by the SDK. Pass an implementation in [com.postedapp.posted.PostedConfig]
 * to replace the default `SharedPreferences` store, for example with MMKV, DataStore or
 * EncryptedSharedPreferences.
 *
 * The SDK stores two keys, both prefixed with `Posted_`: the "install reported" flag and a device
 * ID. If storage is cleared, the install is reported again.
 */
interface PostedStorage {
    /** Returns the stored value for [key], or null if there is none. */
    suspend fun getItem(key: String): String?

    /** Stores [value] under [key], replacing any existing value. */
    suspend fun setItem(
        key: String,
        value: String,
    )
}
