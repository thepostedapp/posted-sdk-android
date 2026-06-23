package com.postedapp.posted.api

import android.util.Log
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.FlutterSdkHeaderResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API client for posted-sdk analytics endpoints.
 *
 * All methods are fire-and-forget: they log warnings on failure and never throw.
 */
internal class AnalyticsApiClient(private val config: PostedConfig) {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Send an analytics event.
     *
     * @param eventName Name of the event
     * @param data Optional event data
     * @param deviceId Persistent device identifier
     */
    suspend fun sendEvent(eventName: String, data: Any?, deviceId: String) =
        withContext(Dispatchers.IO) {
            try {
                val payload = buildMap<String, Any?> {
                    put("event_name", eventName)
                    put("data", data)
                    put("timestamp", isoFormat.format(Date()))
                    put("platform", "android")
                    put("device_id", deviceId)
                }

                val json = HttpClient.gson.toJson(payload)
                val requestBody = json.toRequestBody(HttpClient.JSON)

                val request = Request.Builder()
                    .url(EVENT_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("X-App-ID", config.appId)
                    .addHeader("X-SDK", FlutterSdkHeaderResolver.sdkHeaderValue)
                    .build()

                HttpClient.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "[Posted:ANALYTICS] Event send failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Posted:ANALYTICS] Event send exception", e)
            }
        }

    /**
     * Send a retention event.
     *
     * @param eventName Name of the retention event
     * @param deviceId Persistent device identifier
     */
    suspend fun sendRetentionEvent(eventName: String, deviceId: String) =
        withContext(Dispatchers.IO) {
            try {
                val payload = mapOf(
                    "event_name" to eventName,
                    "timestamp" to isoFormat.format(Date()),
                    "platform" to "android",
                    "device_id" to deviceId
                )

                val json = HttpClient.gson.toJson(payload)
                val requestBody = json.toRequestBody(HttpClient.JSON)

                val request = Request.Builder()
                    .url(RETENTION_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("X-App-ID", config.appId)
                    .addHeader("X-SDK", FlutterSdkHeaderResolver.sdkHeaderValue)
                    .build()

                HttpClient.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "[Posted:ANALYTICS] Retention send failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Posted:ANALYTICS] Retention send exception", e)
            }
        }

    companion object {
        private const val TAG = "AnalyticsApiClient"
        // TODO(POS-39 B3): Update these URLs to the new backend domain once confirmed live/redirecting
        private const val EVENT_URL = "https://godetour.dev/api/analytics/event"
        private const val RETENTION_URL = "https://godetour.dev/api/analytics/retention"
    }
}
