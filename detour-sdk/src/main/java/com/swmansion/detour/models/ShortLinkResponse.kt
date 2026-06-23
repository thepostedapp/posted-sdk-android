package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

/**
 * API response from the short-link resolution endpoint.
 *
 * Fields map to the JSON returned by `POST /api/link/resolve-short`:
 * ```json
 * { "link": "https://...", "route": "/products/123", "parameters": "{...}" }
 * ```
 */
data class ShortLinkResponse(
    @SerializedName("link") val link: String,
    @SerializedName("route") val route: String?,
    @SerializedName("parameters") val parameters: String?,
)
