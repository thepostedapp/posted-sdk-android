package com.postedapp.posted.models

/**
 * Result of link processing.
 *
 * Mirrors the React Native SDK's link result types.
 */
sealed class LinkResult {
    /**
     * Link successfully processed.
     *
     * @property url Full URL that was matched or opened
     * @property route Extracted route for navigation (path + query, first segment stripped)
     * @property pathname Route path without query string
     * @property type Source of the link (DEFERRED, VERIFIED, or SCHEME)
     * @property params Parsed query parameters from the URL
     */
    data class Success(
        val url: String,
        val route: String,
        val pathname: String,
        val type: LinkType,
        val params: Map<String, String> = emptyMap()
    ) : LinkResult()

    /**
     * No deferred link available - not first launch.
     * Returned when deferred link resolution is attempted on subsequent app launches.
     */
    data object NotFirstLaunch : LinkResult()

    /**
     * No link found - normal app launch without any deep link.
     */
    data object NoLink : LinkResult()

    /**
     * Error occurred during link processing.
     *
     * @property exception The exception that occurred
     */
    data class Error(val exception: Exception) : LinkResult()
}
