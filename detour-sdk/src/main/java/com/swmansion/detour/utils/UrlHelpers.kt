package com.swmansion.detour.utils

import android.net.Uri
import android.util.Log
import java.net.URL
import java.net.URLDecoder

/**
 * Utilities for URL parsing and route extraction.
 *
 * The "route" concept: Detour links include an app-hash as the first path segment
 * (e.g. `https://link.example.com/nkeFLNfFBf/products/123`). All parse functions
 * strip that first segment so the consumer receives `/products/123`.
 */
internal object UrlHelpers {
    private const val TAG = "UrlHelpers"

    /**
     * Parse a link and extract the route for navigation.
     * Strips the first path segment (app hash) from both full URLs and path-only strings.
     *
     * Mirrors the React Native SDK's `resolveLink` + `getRestOfPath` behavior.
     *
     * Examples:
     * - `"https://example.com/hash/product/123?c=red"` → `"/product/123?c=red"`
     * - `"//example.com/hash/product/123"`               → `"/product/123"`
     * - `"/hash/product/123?c=red"`                     → `"/product/123?c=red"`
     * - `"hash/product/123"`                            → `"/product/123"`
     * - `"/hash"`                                       → `"/"`
     *
     * @param link The link to parse (full URL or path)
     * @return Extracted route, or null on blank/invalid input
     */
    internal fun parseRoute(link: String?): String? {
        if (link.isNullOrBlank()) return null

        return try {
            when {
                link.startsWith("http://") || link.startsWith("https://") || link.startsWith("//") -> {
                    val url = if (link.startsWith("//")) URL("https:$link") else URL(link)
                    val cleanedPath = removeFirstPathSegment(url.path)
                    val query = url.query
                    if (query.isNullOrBlank()) cleanedPath else "$cleanedPath?$query"
                }
                else -> {
                    // Path-only string — still strip the first segment (app hash)
                    val path = if (link.startsWith("/")) link else "/$link"
                    val queryIndex = path.indexOf('?')
                    val pathPart = if (queryIndex >= 0) path.substring(0, queryIndex) else path
                    val queryPart = if (queryIndex >= 0) path.substring(queryIndex + 1) else null
                    val cleanedPath = removeFirstPathSegment(pathPart)
                    if (queryPart.isNullOrBlank()) cleanedPath else "$cleanedPath?$queryPart"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:TYPE_ERROR] Error parsing URL", e)
            null
        }
    }

    /**
     * Check whether a URL uses http or https scheme.
     */
    internal fun isWebUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")
    }

    /**
     * Extract the navigation route from a custom-scheme deep link.
     * Mirrors the RN SDK's `getRouteFromDeepLink()`.
     *
     * Example: `myapp://product/123?color=red` → `"/product/123?color=red"`
     */
    internal fun getRouteFromDeepLink(uri: Uri): String {
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        val query = uri.query

        val hostPart = if (host.isBlank()) "" else "/$host"
        val pathPart =
            when {
                path.isBlank() -> ""
                path.startsWith("/") -> path
                else -> "/$path"
            }
        val baseRoute = if (hostPart.isBlank() && pathPart.isBlank()) "/" else "$hostPart$pathPart"

        return if (query.isNullOrBlank()) {
            baseRoute
        } else {
            "$baseRoute?$query"
        }
    }

    /**
     * Check whether a URI has exactly one path segment (indicating a short link).
     */
    internal fun isSingleSegmentPath(uri: Uri): Boolean {
        return uri.pathSegments.size == 1
    }

    /**
     * Parse query parameters from a URL or route string into a map.
     *
     * Example: `"/products/123?color=red&size=L"` → `{color=red, size=L}`
     */
    internal fun parseQueryParams(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return emptyMap()

        val query = url.substring(queryStart + 1)
        if (query.isBlank()) return emptyMap()

        return query.split('&').mapNotNull { param ->
            val eqIndex = param.indexOf('=')
            if (eqIndex > 0) {
                val key = URLDecoder.decode(param.substring(0, eqIndex), "UTF-8")
                val value = URLDecoder.decode(param.substring(eqIndex + 1), "UTF-8")
                key to value
            } else if (param.isNotBlank()) {
                URLDecoder.decode(param, "UTF-8") to ""
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Extract the pathname (path without query string) from a route.
     *
     * Example: `"/products/123?color=red"` → `"/products/123"`
     */
    internal fun extractPathname(route: String?): String {
        if (route.isNullOrBlank()) return "/"
        val queryIndex = route.indexOf('?')
        return if (queryIndex >= 0) route.substring(0, queryIndex) else route
    }

    /**
     * Remove the first path segment (app hash) from a pathname.
     * Mirrors the RN SDK's `getRestOfPath()`.
     *
     * Examples:
     * - `"/hash/product/123"` → `"/product/123"`
     * - `"/hash"`             → `"/"`
     * - `"/"`                 → `"/"`
     */
    private fun removeFirstPathSegment(path: String): String {
        if (path.isBlank() || path == "/") return "/"

        val secondSlashIndex = path.indexOf('/', 1)
        return if (secondSlashIndex == -1) "/" else path.substring(secondSlashIndex)
    }
}
