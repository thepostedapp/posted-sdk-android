package com.postedapp.posted.api

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.postedapp.posted.FlutterSdkHeaderResolver
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.models.DeviceFingerprint
import com.postedapp.posted.models.LinkMatchResponse
import com.postedapp.posted.models.ShortLinkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * API client for posted-sdk link-matching and short-link resolution.
 *
 * NOTE: Backend API domain. Cut over from the legacy host to attr.postedapp.com (POS-336).
 * is confirmed live/redirecting by the board. See POS-39 prerequisites.
 */
internal class PostedApiClient(
    private val config: PostedConfig,
    private val context: Context,
    private val baseUrl: String = "https://attr.postedapp.com",
) {
    /**
     * Match link using fingerprint data.
     *
     * @param fingerprint Device fingerprint (probabilistic or deterministic)
     * @return Matched link or null
     */
    suspend fun matchLink(fingerprint: DeviceFingerprint): String? =
        withContext(Dispatchers.IO) {
            val json = HttpClient.gson.toJson(fingerprint)
            Log.d(TAG, "Sending fingerprint to API")

            val request =
                Request.Builder()
                    .url(matchLinkUrl)
                    .post(json.toRequestBody(HttpClient.JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("X-App-ID", config.appId)
                    .addHeader("X-SDK", FlutterSdkHeaderResolver.sdkHeaderValue)
                    .build()

            HttpClient.okHttp.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string() ?: return@use null
                        val linkResponse = HttpClient.gson.fromJson(responseBody, LinkMatchResponse::class.java)
                        if (linkResponse.link != null) Log.d(TAG, "Link matched successfully")
                        linkResponse.link
                    }
                    response.code == 404 -> {
                        Log.d(TAG, "No matching link found")
                        null
                    }
                    else -> {
                        val errorMessage = response.body?.string()?.takeIf { it.isNotBlank() } ?: response.message
                        throw IOException("[${response.code}] $errorMessage")
                    }
                }
            }
        }

    /**
     * Resolve a short link to its full link data.
     *
     * @param url The short link URL to resolve
     * @return ShortLinkResponse or null on 404/error (matches RN SDK behavior)
     */
    suspend fun resolveShortLink(url: String): ShortLinkResponse? =
        withContext(Dispatchers.IO) {
            try {
                val payload = HttpClient.gson.toJson(mapOf("url" to url))
                val requestBody = payload.toRequestBody(HttpClient.JSON)

                val request =
                    Request.Builder()
                        .url(resolveShortUrl)
                        .post(requestBody)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .addHeader("X-App-ID", config.appId)
                        .addHeader("X-SDK", FlutterSdkHeaderResolver.sdkHeaderValue)
                        .build()

                HttpClient.okHttp.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            return@use HttpClient.gson.fromJson(
                                responseBody,
                                ShortLinkResponse::class.java,
                            )
                        }
                    } else {
                        Log.w(TAG, "[Posted:NETWORK_ERROR] Short link resolution failed: ${response.code}")
                    }
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Posted:NETWORK_ERROR] Short link resolution exception", e)
                null
            }
        }

    private data class UniversalLinkClickResponse(
        val allowed: Boolean?,
        val clickId: String?,
        val error: String?,
        val code: String?,
        val clicksInPeriod: Int?,
        val effectiveLimit: Int?,
    )

    data class UniversalLinkClickResult(
        val allowed: Boolean,
        val clickId: String? = null,
        val error: String? = null,
        val code: String? = null,
        val clicksInPeriod: Int? = null,
        val effectiveLimit: Int? = null,
    )

    suspend fun sendUniversalLinkClick(
        url: String,
        params: Map<String, String> = emptyMap(),
    ): UniversalLinkClickResult =
        withContext(Dispatchers.IO) {
            try {
                val body =
                    mutableMapOf<String, Any>(
                        "url" to url,
                        "timestamp" to System.currentTimeMillis(),
                        "platform" to "android",
                        "metadata" to buildMetadata(),
                    )
                if (params.isNotEmpty()) body["params"] = params
                val payload = HttpClient.gson.toJson(body)
                val request =
                    Request.Builder()
                        .url(universalLinkClickUrl)
                        .post(payload.toRequestBody(HttpClient.JSON))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .addHeader("X-App-ID", config.appId)
                        .addHeader("X-SDK", FlutterSdkHeaderResolver.sdkHeaderValue)
                        .build()

                HttpClient.shortTimeoutOkHttp.newCall(request).execute().use { response ->
                    val bodyString =
                        try {
                            response.body?.string()
                        } catch (e: Exception) {
                            null
                        }
                    val parsed =
                        bodyString?.let {
                            runCatching {
                                HttpClient.gson.fromJson(it, UniversalLinkClickResponse::class.java)
                            }.getOrNull()
                        }

                    val isExplicitDeny = parsed?.allowed == false || response.code == 402
                    if (isExplicitDeny) {
                        return@use UniversalLinkClickResult(
                            allowed = false,
                            error = parsed?.error ?: "Click limit exceeded",
                            code = parsed?.code,
                            clicksInPeriod = parsed?.clicksInPeriod,
                            effectiveLimit = parsed?.effectiveLimit,
                        )
                    }

                    // Fail-open for temporary backend/network issues so apps keep working.
                    UniversalLinkClickResult(allowed = true, clickId = parsed?.clickId)
                }
            } catch (e: Exception) {
                // Fail-open on transport errors; limit enforcement only happens on explicit deny.
                Log.w(TAG, "[Posted:NETWORK_ERROR] sendUniversalLinkClick failed, allowing through", e)
                UniversalLinkClickResult(allowed = true)
            }
        }

    private fun buildMetadata(): Map<String, String?> {
        val appVersion =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0),
                    ).versionName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }
            }.getOrNull()

        return mapOf(
            "os_version" to Build.VERSION.RELEASE,
            "app_version" to appVersion,
            "device_model" to Build.MODEL,
        )
    }

    /** True when [host] is the same host as [baseUrl] (the known short-link domain). */
    internal fun isKnownShortLinkHost(host: String): Boolean {
        if (host.isBlank()) return false
        val baseHost = Uri.parse(baseUrl).host ?: return false
        return host.equals(baseHost, ignoreCase = true)
    }

    private val matchLinkUrl get() = "$baseUrl/api/link/match-link"
    private val resolveShortUrl get() = "$baseUrl/api/link/resolve-short"
    private val universalLinkClickUrl get() = "$baseUrl/api/link/universal-link-click"

    companion object {
        private const val TAG = "PostedApiClient"
    }
}
