package com.swmansion.detour.models

/**
 * Type of deep link processed by Detour SDK.
 */
enum class LinkType {
    /**
     * Deferred deep link - user clicked link before app was installed.
     * Matched via API call with device fingerprint.
     */
    DEFERRED,

    /**
     * Verified App Link - user clicked an http/https link with the app already installed.
     * Domain ownership is verified via Digital Asset Links (Android App Links).
     */
    VERIFIED,

    /**
     * Custom scheme deep link (e.g. myapp://product/123).
     */
    SCHEME,
}
