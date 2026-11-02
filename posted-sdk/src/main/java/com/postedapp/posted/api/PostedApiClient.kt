package com.postedapp.posted.api

import android.util.Log
import com.postedapp.posted.FlutterSdkHeaderResolver
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.models.ProbabilisticFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * API client for the install report.
 */
internal class PostedApiClient(
    private val config: PostedConfig,
    private val baseUrl: String = "https://attr.postedapp.com",
) {
    /**
     * POSTs the install report. Returns true only on a 2xx, so the caller arms its sent-once
     * flag only when the backend accepted the install. Never throws.
     *
     * The backend's install schema is the fingerprint's fields plus `device_id`, and `clickId`
     * when the Play install referrer carried one, in one flat JSON object.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun reportInstall(
        fingerprint: ProbabilisticFingerprint,
        deviceId: String,
        clickId: String?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = HttpClient.gson.toJsonTree(fingerprint).asJsonObject
                body.addProperty("device_id", deviceId)
                if (clickId != null) body.addProperty("clickId", clickId)

                val request =
                    Request.Builder()
                        .url("$baseUrl/api/install")
                        .post(HttpClient.gson.toJson(body).toRequestBody(HttpClient.JSON))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .addHeader("X-App-ID", config.appId)
                        .addHeader("X-SDK", FlutterSdkHeaderResolver.sdkHeaderValue)
                        .build()

                HttpClient.okHttp.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Install reported")
                        true
                    } else {
                        Log.w(TAG, "[Posted:INSTALL_ERROR] Failed to report install: ${response.code}")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Posted:INSTALL_ERROR] Error reporting install", e)
                false
            }
        }

    companion object {
        private const val TAG = "PostedApiClient"
    }
}
