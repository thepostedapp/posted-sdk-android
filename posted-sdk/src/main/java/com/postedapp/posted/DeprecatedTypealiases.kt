@file:Suppress("unused")

package com.postedapp.posted

import com.postedapp.posted.analytics.PostedAnalytics
import com.postedapp.posted.analytics.PostedEventNames
import com.postedapp.posted.storage.PostedStorage

/**
 * Backward-compatibility typealiases for the Detour → posted-sdk rename.
 * These are deprecated and will be removed in the next major version.
 * Migrate to the new names: Posted, PostedConfig, PostedDelegate, PostedStorage,
 * PostedAnalytics, PostedEventNames.
 */

@Deprecated(
    message = "Renamed to Posted",
    replaceWith = ReplaceWith("Posted", "com.postedapp.posted.Posted")
)
typealias Detour = Posted

@Deprecated(
    message = "Renamed to PostedConfig",
    replaceWith = ReplaceWith("PostedConfig", "com.postedapp.posted.PostedConfig")
)
typealias DetourConfig = PostedConfig

@Deprecated(
    message = "Renamed to PostedDelegate",
    replaceWith = ReplaceWith("PostedDelegate", "com.postedapp.posted.PostedDelegate")
)
typealias DetourDelegate = PostedDelegate

@Deprecated(
    message = "Renamed to PostedStorage",
    replaceWith = ReplaceWith("PostedStorage", "com.postedapp.posted.storage.PostedStorage")
)
typealias DetourStorage = PostedStorage

@Deprecated(
    message = "Renamed to PostedAnalytics",
    replaceWith = ReplaceWith("PostedAnalytics", "com.postedapp.posted.analytics.PostedAnalytics")
)
typealias DetourAnalytics = PostedAnalytics

@Deprecated(
    message = "Renamed to PostedEventNames",
    replaceWith = ReplaceWith("PostedEventNames", "com.postedapp.posted.analytics.PostedEventNames")
)
typealias DetourEventNames = PostedEventNames
