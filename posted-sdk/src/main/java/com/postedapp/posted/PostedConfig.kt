package com.postedapp.posted

import com.postedapp.posted.models.LinkProcessingMode
import com.postedapp.posted.storage.PostedStorage

/**
 * Configuration for the posted-sdk SDK.
 *
 * @property apiKey Your API key from the posted dashboard
 * @property appId Your application ID from the posted dashboard
 * @property linkProcessingMode Controls which link sources are handled (default: [LinkProcessingMode.ALL])
 * @property storage Custom storage implementation for persisting SDK data.
 *                   Defaults to SharedPreferences-based storage.
 */
data class PostedConfig(
    val apiKey: String,
    val appId: String,
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,
    val storage: PostedStorage? = null,
) {
    /**
     * Builder for Java callers (data class defaults don't work from Java).
     *
     * ```java
     * PostedConfig config = new PostedConfig.Builder("apiKey", "appId")
     *     .linkProcessingMode(LinkProcessingMode.WEB_ONLY)
     *     .build();
     * ```
     */
    class Builder(private val apiKey: String, private val appId: String) {
        private var linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL
        private var storage: PostedStorage? = null

        fun linkProcessingMode(value: LinkProcessingMode) = apply { linkProcessingMode = value }

        fun storage(value: PostedStorage) = apply { storage = value }

        fun build(): PostedConfig =
            PostedConfig(
                apiKey = apiKey,
                appId = appId,
                linkProcessingMode = linkProcessingMode,
                storage = storage,
            )
    }
}
