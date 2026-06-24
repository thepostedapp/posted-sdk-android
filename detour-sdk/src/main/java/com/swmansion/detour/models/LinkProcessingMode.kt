package com.swmansion.detour.models

/**
 * Controls which link sources are handled by the SDK.
 *
 * Mirrors the React Native SDK's `linkProcessingMode` configuration.
 */
enum class LinkProcessingMode {
    /**
     * Handle all link types: deferred links, Universal/App Links, and custom scheme links.
     * This is the default mode.
     */
    ALL,

    /**
     * Handle deferred links and Universal/App Links (http/https), but NOT custom scheme links.
     * Use this when another library handles custom scheme routing.
     */
    WEB_ONLY,

    /**
     * Only handle deferred links. No runtime link listener, no initial URL check, no scheme links.
     * Recommended when your navigation framework already resolves runtime and initial links.
     */
    DEFERRED_ONLY,
}
