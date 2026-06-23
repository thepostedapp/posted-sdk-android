package com.swmansion.detour.api

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP and JSON singleton.
 *
 * OkHttp mandates a single client instance for connection pooling.
 * A single Gson instance avoids repeated reflection overhead.
 */
internal object HttpClient {
    val okHttp: OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    // Shared connection pool, tighter call timeout for link-gate requests.
    val shortTimeoutOkHttp: OkHttpClient =
        okHttp.newBuilder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

    val gson: Gson = Gson()

    val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
}
