package com.postedapp.posted

import com.postedapp.posted.storage.PostedStorage

/**
 * Credentials for your app, from the Posted dashboard.
 *
 * @property apiKey Your API key from the dashboard.
 * @property appId Your app ID from the dashboard.
 * @property storage Persistent key-value storage. Defaults to `SharedPreferences`.
 */
data class PostedConfig(
    val apiKey: String,
    val appId: String,
    val storage: PostedStorage? = null,
) {
    /**
     * Builder for Java callers (data class defaults don't work from Java).
     *
     * ```java
     * PostedConfig config = new PostedConfig.Builder("apiKey", "appId").build();
     * ```
     */
    class Builder(private val apiKey: String, private val appId: String) {
        private var storage: PostedStorage? = null

        fun storage(value: PostedStorage) = apply { storage = value }

        fun build(): PostedConfig = PostedConfig(apiKey = apiKey, appId = appId, storage = storage)
    }
}
